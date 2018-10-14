/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package routing;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import core.Application;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.Settings;
import core.SettingsError;
import core.SimClock;
import core.SimError;
import routing.util.RoutingInfo;
import util.Tuple;

/**
 * Superclass for message routers.
 */
public abstract class MessageRouter {
	/** Message buffer size -setting id ({@value}). Long value in bytes.*/
	public static final String B_SIZE_S = "bufferSize";//每个节点消息缓存大小
	/**
	 * Message TTL -setting id ({@value}). Value is in minutes and must be
	 * an integer.
	 */
	public static final String MSG_TTL_S = "msgTtl";//消息的生存周期
	/**
	 * Message/fragment sending queue type -setting id ({@value}).
	 * This setting affects the order the messages and fragments are sent if the
	 * routing protocol doesn't define any particular order (e.g, if more than
	 * one message can be sent directly to the final recipient).
	 * Valid values are<BR>
	 * <UL>
	 * <LI/> 1 : random (message order is randomized every time; default option)
	 * <LI/> 2 : FIFO (most recently received messages are sent last)
	 * </UL>
	 */
	public static final String SEND_QUEUE_MODE_S = "sendQueue";//发送队列模式，两种——先入先出和随机

	/** Setting value for random queue mode */
	public static final int Q_MODE_RANDOM = 1;
	/** Setting value for FIFO queue mode */
	public static final int Q_MODE_FIFO = 2;

	/** Setting string for random queue mode */
	public static final String STR_Q_MODE_RANDOM = "RANDOM";
	/** Setting string for FIFO queue mode */
	public static final String STR_Q_MODE_FIFO = "FIFO";

	/* Return values when asking to start a transmission:
	 * RCV_OK (0) means that the host accepts the message and transfer started,
	 * values < 0 mean that the  receiving host will not accept this
	 * particular message (right now),
	 * values > 0 mean the host will not right now accept any message.
	 * Values in the range [-100, 100] are reserved for general return values
	 * (and specified here), values beyond that are free for use in
	 * implementation specific cases */
	//0表示节点接收message并且转发开始，
	//小于0的值表示接收节点并没有接收该信息；
	//大于0的值表示目前不能接收该信息
	/** Receive return value for OK */
	public static final int RCV_OK = 0;
	/** Receive return value for busy receiver */
	public static final int TRY_LATER_BUSY = 1;
	/** Receive return value for an old (already received) message */
	public static final int DENIED_OLD = -1;
	/** Receive return value for not enough space in the buffer for the msg */
	public static final int DENIED_NO_SPACE = -2;
	/** Receive return value for messages whose TTL has expired */
	public static final int DENIED_TTL = -3;
	/** Receive return value for a node low on some resource(s) */
	public static final int DENIED_LOW_RESOURCES = -4;
	/** Receive return value for a node low on some resource(s) */
	public static final int DENIED_POLICY = -5;
	/** Receive return value for unspecified reason */
	public static final int DENIED_UNSPECIFIED = -99;

	private List<MessageListener> mListeners;//消息监听器列表
	/** The messages being transferred with msgID_hostName keys */
	private HashMap<String, Message> incomingMessages;//正在等待转发的消息：不妨称为：缓存消息列表
	/** The messages this router is carrying */
	private HashMap<String, Message> messages;//该路由模块携带的所有消息都存在HashMap：自身产生的消息已经其他主机传过来的目的主机不是自己的消息    不妨称为：待发送消息列表
	/** The messages this router has received as the final recipient */
	private HashMap<String, Message> deliveredMessages;//该节点作为终端节点，接收到的消息都存在该HashMap：相当于内存      不妨称为：已接受消息列表
	/** The messages that Applications on this router have blacklisted */
	private HashMap<String, Object> blacklistedMessages;//该路由模块上承载的应用程序中的黑名单消息
	/** Host where this router belongs to */
	private DTNHost host;//该路由模块的所属主机
	/** size of the buffer */
	private long bufferSize;//该路由模块缓存大小：这个缓存大小仅仅用来限制messages的大小
	/** TTL for all messages */
	protected int msgTtl;//所有消息的ttl
	/** Queue mode for sending messages */
	private int sendQueueMode;//发送队列模式

	/** applications attached to the host */
	private HashMap<String, Collection<Application>> applications = null;//该路由模块上承载的应用程序的集合

	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object. Size of the message buffer is read from
	 * {@link #B_SIZE_S} setting. Default value is Integer.MAX_VALUE.
	 * @param s The settings object
	 */
	//构造函数只是根据设置文件，初始化一些各消息模块可以共有的信息：如：缓存大小，ttl，消息发送方式
	public MessageRouter(Settings s) {
		this.bufferSize = Integer.MAX_VALUE; // defaults to rather large buffer//路由模块缓存大小默认为无穷大
		this.msgTtl = Message.INFINITE_TTL;//所有消息的ttl默认为无穷大
		this.applications = new HashMap<String, Collection<Application>>();

		if (s.contains(B_SIZE_S)) {
			this.bufferSize = s.getLong(B_SIZE_S);//Group4.bufferSize = 50M
		}

		if (s.contains(MSG_TTL_S)) {//Group.msgTtl = 300
			this.msgTtl = s.getInt(MSG_TTL_S);
		}

		if (s.contains(SEND_QUEUE_MODE_S)) {

			String mode = s.getSetting(SEND_QUEUE_MODE_S);

			if (mode.trim().toUpperCase().equals(STR_Q_MODE_FIFO)) {
				this.sendQueueMode = Q_MODE_FIFO;
			} else if (mode.trim().toUpperCase().equals(STR_Q_MODE_RANDOM)){
				this.sendQueueMode = Q_MODE_RANDOM;
			} else {
				this.sendQueueMode = s.getInt(SEND_QUEUE_MODE_S);
				if (sendQueueMode < 1 || sendQueueMode > 2) {
					throw new SettingsError("Invalid value for " +
							s.getFullPropertyName(SEND_QUEUE_MODE_S));
				}
			}
		}
		else {
			sendQueueMode = Q_MODE_RANDOM;
		}
	}

	/**
	 * Initializes the router; i.e. sets the host this router is in and
	 * message listeners that need to be informed about message related
	 * events etc.
	 * @param host The host this router is in
	 * @param mListeners The message listeners
	 */
	//该init函数用于初始化路由模块的特有信息
	public void init(DTNHost host, List<MessageListener> mListeners) {
		this.incomingMessages = new HashMap<String, Message>();
		this.messages = new HashMap<String, Message>();
		this.deliveredMessages = new HashMap<String, Message>();
		this.blacklistedMessages = new HashMap<String, Object>();
		this.mListeners = mListeners;
		this.host = host;
	}

	/**
	 * Copy-constructor.
	 * @param r Router to copy the settings from.
	 */
	//复制构造函数，复制各消息模块可以共有的信息，如：缓存大小，ttl，消息发送方式，应用程序
	protected MessageRouter(MessageRouter r) {
		this.bufferSize = r.bufferSize;
		this.msgTtl = r.msgTtl;
		this.sendQueueMode = r.sendQueueMode;

		this.applications = new HashMap<String, Collection<Application>>();
		for (Collection<Application> apps : r.applications.values()) {
			for (Application app : apps) {
				addApplication(app.replicate());
			}
		}
	}

	/**
	 * Updates router.
	 * This method should be called (at least once) on every simulation
	 * interval to update the status of transfer(s).
	 */
	//该方法在每次仿真间隔内调用一次(至少调用一次)以更新传输的状态
	public void update(){
		for (Collection<Application> apps : this.applications.values()) {
			for (Application app : apps) {
				app.update(this.host);
			}
		}
	}

	/**
	 * Informs the router about change in connections state.
	 * @param con The connection that changed
	 */
	//通知路由连接状态改变
	public abstract void changedConnection(Connection con);

	/**
	 * Returns a message by ID.
	 * @param id ID of the message
	 * @return The message
	 */
	protected Message getMessage(String id) {
		return this.messages.get(id);
	}

	/**
	 * Checks if this router has a message with certain id buffered.
	 * @param id Identifier of the message
	 * @return True if the router has message with this id, false if not
	 */
	public boolean hasMessage(String id) {
		return this.messages.containsKey(id);
	}

	/**
	 * Returns true if a full message with same ID as the given message has been
	 * received by this host as the <strong>final</strong> recipient
	 * (at least once).
	 * @param m message we're interested of
	 * @return true if a message with the same ID has been received by
	 * this host as the final recipient.
	 */
	//如果该节点作为消息终点接收到参数message，返回true
	protected boolean isDeliveredMessage(Message m) {
		return (this.deliveredMessages.containsKey(m.getId()));
	}

	/**
	 * Returns <code>true</code> if the message has been blacklisted. Messages
	 * get blacklisted when an application running on the node wants to drop it.
	 * This ensures the peer doesn't try to constantly send the same message to
	 * this node, just to get dropped by an application every time.
	 *
	 * @param id	id of the message
	 * @return <code>true</code> if blacklisted, <code>false</code> otherwise.
	 */
	protected boolean isBlacklistedMessage(String id) {
		return this.blacklistedMessages.containsKey(id);
	}

	/**
	 * Returns a reference to the messages of this router in collection.
	 * <b>Note:</b> If there's a chance that some message(s) from the collection
	 * could be deleted (or added) while iterating through the collection, a
	 * copy of the collection should be made to avoid concurrent modification
	 * exceptions.
	 * @return a reference to the messages of this router in collection
	 */
	public Collection<Message> getMessageCollection() {
		return this.messages.values();
	}

	/**
	 * Returns the number of messages this router has
	 * @return How many messages this router has
	 */
	public int getNrofMessages() {
		return this.messages.size();
	}

	/**
	 * Returns the size of the message buffer.
	 * @return The size or Integer.MAX_VALUE if the size isn't defined.
	 */
	public long getBufferSize() {
		return this.bufferSize;
	}

	/**
	 * Returns the amount of free space in the buffer. May return a negative
	 * value if there are more messages in the buffer than should fit there
	 * (because of creating new messages).
	 * @return The amount of free space (Integer.MAX_VALUE if the buffer
	 * size isn't defined)
	 */
	public long getFreeBufferSize() {
		long occupancy = 0;

		if (this.getBufferSize() == Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}

		for (Message m : getMessageCollection()) {
			occupancy += m.getSize();
		}

		return this.getBufferSize() - occupancy;
	}

	/**
	 * Returns the host this router is in
	 * @return The host object
	 */
	protected DTNHost getHost() {
		return this.host;
	}

	/**
	 * Start sending a message to another host.
	 * @param id Id of the message to send
	 * @param to The host to send the message to
	 */
	//开始发送message给另一个节点
	public void sendMessage(String id, DTNHost to) {
		Message m = getMessage(id);
		Message m2;
		if (m == null) throw new SimError("no message for id " +
				id + " to send at " + this.host);

		m2 = m.replicate();	// send a replicate of the message
		to.receiveMessage(m2, this.host);
	}

	/**
	 * Requests for deliverable message from this router to be sent trough a
	 * connection.
	 * @param con The connection to send the messages trough
	 * @return True if this router started a transfer, false if not
	 */
	//请求通过已建立的连接通道发送消息，若要使用需要被子类重载
	public boolean requestDeliverableMessages(Connection con) {
		return false; // default behavior is to not start -- subclasses override
	}

	/**
	 * Try to start receiving a message from another host.
	 * @param m Message to put in the receiving buffer
	 * @param from Who the message is from
	 * @return Value zero if the node accepted the message (RCV_OK), value less
	 * than zero if node rejected the message (e.g. DENIED_OLD), value bigger
	 * than zero if the other node should try later (e.g. TRY_LATER_BUSY).
	 */
	//开始接收从另一节点发送过来的message。
	//将接收到信息添加到incomingBuffer中，
	//将本地节点加入到message的路径表中，
	//然后，message监视器得到接受信息。
	//因为该方法一直返回ok，所以基本应该是需要被重写的
	public int receiveMessage(Message m, DTNHost from) {
		Message newMessage = m.replicate();

		this.putToIncomingBuffer(newMessage, from);
		newMessage.addNodeOnPath(this.host);

		for (MessageListener ml : this.mListeners) {
			ml.messageTransferStarted(newMessage, from, getHost());
		}

		return RCV_OK; // superclass always accepts messages
	}

	/**
	 * This method should be called (on the receiving host) after a message
	 * was successfully transferred. The transferred message is put to the
	 * message buffer unless this host is the final recipient of the message.
	 * @param id Id of the transferred message
	 * @param from Host the message was from (previous hop)
	 * @return The message that this host received
	 */
	//当一个message成功被转发，该函数由消息接收端发起。
	//先将转发的message从incoming列表中取出，然后设置接收时间戳；
	//将message交给application（如果有的话），application接收一个输入message，处理后返回一个输出message，
	//如果输出message 被app重新定义了目的地，那么本message将不会算作“转发给了”本节点。
	//判定该message是否到达最终目的，判定是否是第一次被转发（本地节点是最终目的，并且之前没有被转发过）。
	//如果不是最终目的，而且app不想扔掉该message，则将其加入缓存，存入messages表；
	//否则，如果是第一次转发节点，将其添加到deliveredMessages表中；
	//否则，如果没有输出，将其加入黑名单，表示不再接受该message。
	public Message messageTransferred(String id, DTNHost from) {
		Message incoming = removeFromIncomingBuffer(id, from);
		boolean isFinalRecipient;
		boolean isFirstDelivery; // is this first delivered instance of the msg


		if (incoming == null) {
			throw new SimError("No message with ID " + id + " in the incoming "+
					"buffer of " + this.host);
		}

		incoming.setReceiveTime(SimClock.getTime());

		// Pass the message to the application (if any) and get outgoing message
		Message outgoing = incoming;
		for (Application app : getApplications(incoming.getAppID())) {
			// Note that the order of applications is significant
			// since the next one gets the output of the previous.
			outgoing = app.handle(outgoing, this.host);
			if (outgoing == null) break; // Some app wanted to drop the message
		}

		Message aMessage = (outgoing==null)?(incoming):(outgoing);
		// If the application re-targets the message (changes 'to')
		// then the message is not considered as 'delivered' to this host.
		isFinalRecipient = aMessage.getTo() == this.host;
		isFirstDelivery = isFinalRecipient && !isDeliveredMessage(aMessage);

		if (!isFinalRecipient && outgoing!=null) {
			// not the final recipient and app doesn't want to drop the message
			// -> put to buffer
			addToMessages(aMessage, false);
		} else if (isFirstDelivery) {
			this.deliveredMessages.put(id, aMessage);
		} else if (outgoing == null) {
			// Blacklist messages that an app wants to drop.
			// Otherwise the peer will just try to send it back again.
			this.blacklistedMessages.put(id, null);
		}

		for (MessageListener ml : this.mListeners) {
			ml.messageTransferred(aMessage, from, this.host,
					isFirstDelivery);
		}

		return aMessage;
	}

	/**
	 * Puts a message to incoming messages buffer. Two messages with the
	 * same ID are distinguished by the from host.
	 * @param m The message to put
	 * @param from Who the message was from (previous hop).
	 */
	protected void putToIncomingBuffer(Message m, DTNHost from) {
		this.incomingMessages.put(m.getId() + "_" + from.toString(), m);
	}

	/**
	 * Removes and returns a message with a certain ID from the incoming
	 * messages buffer or null if such message wasn't found.
	 * @param id ID of the message
	 * @param from The host that sent this message (previous hop)
	 * @return The found message or null if such message wasn't found
	 */
	protected Message removeFromIncomingBuffer(String id, DTNHost from) {
		return this.incomingMessages.remove(id + "_" + from.toString());
	}

	/**
	 * Returns true if a message with the given ID is one of the
	 * currently incoming messages, false if not
	 * @param id ID of the message
	 * @return True if such message is incoming right now
	 */
	protected boolean isIncomingMessage(String id) {
		return this.incomingMessages.containsKey(id);
	}

	/**
	 * Adds a message to the message buffer and informs message listeners
	 * about new message (if requested).
	 * @param m The message to add
	 * @param newMessage If true, message listeners are informed about a new
	 * message, if false, nothing is informed.
	 */
	//添加一个消息进入内存，
	//若newMessage为true，则通知消息监听器：有新的消息被添加
	//若newMessage为false，则不通知消息监听器
	protected void addToMessages(Message m, boolean newMessage) {
		this.messages.put(m.getId(), m);

		if (newMessage) {
			for (MessageListener ml : this.mListeners) {
				ml.newMessage(m);
			}
		}
	}

	/**
	 * Removes and returns a message from the message buffer.
	 * @param id Identifier of the message to remove
	 * @return The removed message or null if message for the ID wasn't found
	 */
	//从内存中移除消息，并返回该消息
	protected Message removeFromMessages(String id) {
		Message m = this.messages.remove(id);
		return m;
	}

	/**
	 * This method should be called (on the receiving host) when a message
	 * transfer was aborted.
	 * @param id Id of the message that was being transferred
	 * @param from Host the message was from (previous hop)
	 * @param bytesRemaining Nrof bytes that were left before the transfer
	 * would have been ready; or -1 if the number of bytes is not known
	 */
	//存储分为缓存和内存；1：消息先传入缓存 2：然后再由缓存转移到内存，
	//第一步永远成功，在执行第二步之前执行messageAborted方法，即为消息传输中断
	//消息传输中断调用该方法，该方法将已缓存的消息从缓存中移除，并通知消息监听器
	public void messageAborted(String id, DTNHost from, int bytesRemaining) {
		Message incoming = removeFromIncomingBuffer(id, from);
		if (incoming == null) {
			throw new SimError("No incoming message for id " + id +
					" to abort in " + this.host);
		}

		for (MessageListener ml : this.mListeners) {
			ml.messageTransferAborted(incoming, from, this.host);
		}
	}

	/**
	 * Creates a new message to the router.
	 * @param m The message to create
	 * @return True if the creation succeeded, false if not (e.g.
	 * the message was too big for the buffer)
	 */
	public boolean createNewMessage(Message m) {
		m.setTtl(this.msgTtl);
		addToMessages(m, true);
		return true;
	}

	/**
	 * Deletes a message from the buffer and informs message listeners
	 * about the event
	 * @param id Identifier of the message to delete
	 * @param drop If the message is dropped (e.g. because of full buffer) this
	 * should be set to true. False value indicates e.g. remove of message
	 * because it was delivered to final destination.
	 */
	//从内存移除消息，并通知消息监听器：消息被移除
	//drop=true；表示因为内存被沾满，该消息被丢弃
	//drop=false：表示该消息因为已经被传送到终端节点，而被丢弃
	public void deleteMessage(String id, boolean drop) {
		Message removed = removeFromMessages(id);
		if (removed == null) throw new SimError("no message for id " +
				id + " to remove at " + this.host);

		for (MessageListener ml : this.mListeners) {
			ml.messageDeleted(removed, this.host, drop);
		}
	}

	/**
	 * Sorts/shuffles the given list according to the current sending queue
	 * mode. The list can contain either Message or Tuple<Message, Connection>
	 * objects. Other objects cause error.
	 * @param list The list to sort or shuffle
	 * @return The sorted/shuffled list
	 */
	@SuppressWarnings(value = "unchecked") /* ugly way to make this generic */
	protected List sortByQueueMode(List list) {
		switch (sendQueueMode) {
		case Q_MODE_RANDOM:
			Collections.shuffle(list, new Random(SimClock.getIntTime()));
			break;
		case Q_MODE_FIFO:
			Collections.sort(list,
					new Comparator() {
				/** Compares two tuples by their messages' receiving time */
				public int compare(Object o1, Object o2) {
					double diff;
					Message m1, m2;

					if (o1 instanceof Tuple) {
						m1 = ((Tuple<Message, Connection>)o1).getKey();
						m2 = ((Tuple<Message, Connection>)o2).getKey();
					}
					else if (o1 instanceof Message) {
						m1 = (Message)o1;
						m2 = (Message)o2;
					}
					else {
						throw new SimError("Invalid type of objects in " +
								"the list");
					}

					diff = m1.getReceiveTime() - m2.getReceiveTime();
					if (diff == 0) {
						return 0;
					}
					return (diff < 0 ? -1 : 1);
				}
			});
			break;
		/* add more queue modes here */
		default:
			throw new SimError("Unknown queue mode " + sendQueueMode);
		}

		return list;
	}

	/**
	 * Gives the order of the two given messages as defined by the current
	 * queue mode
	 * @param m1 The first message
	 * @param m2 The second message
	 * @return -1 if the first message should come first, 1 if the second
	 *          message should come first, or 0 if the ordering isn't defined
	 */
	protected int compareByQueueMode(Message m1, Message m2) {
		switch (sendQueueMode) {
		case Q_MODE_RANDOM:
			/* return randomly (enough) but consistently -1, 0 or 1 */
			int hash_diff = m1.hashCode() - m2.hashCode();
			if (hash_diff == 0) {
				return 0;
			}
			return (hash_diff < 0 ? -1 : 1);
		case Q_MODE_FIFO:
			double diff = m1.getReceiveTime() - m2.getReceiveTime();
			if (diff == 0) {
				return 0;
			}
			return (diff < 0 ? -1 : 1);
		/* add more queue modes here */
		default:
			throw new SimError("Unknown queue mode " + sendQueueMode);
		}
	}

	/**
	 * Returns routing information about this router.
	 * @return The routing information.
	 */
	//返回当前路由器的路由信息
	public RoutingInfo getRoutingInfo() {
		RoutingInfo ri = new RoutingInfo(this);
		RoutingInfo incoming = new RoutingInfo(this.incomingMessages.size() +
				" incoming message(s)");
		RoutingInfo delivered = new RoutingInfo(this.deliveredMessages.size() +
				" delivered message(s)");

		RoutingInfo cons = new RoutingInfo(host.getConnections().size() +
			" connection(s)");

		ri.addMoreInfo(incoming);
		ri.addMoreInfo(delivered);
		ri.addMoreInfo(cons);

		for (Message m : this.incomingMessages.values()) {
			incoming.addMoreInfo(new RoutingInfo(m));
		}

		for (Message m : this.deliveredMessages.values()) {
			delivered.addMoreInfo(new RoutingInfo(m + " path:" + m.getHops()));
		}

		for (Connection c : host.getConnections()) {
			cons.addMoreInfo(new RoutingInfo(c));
		}

		return ri;
	}

	/**
	 * Adds an application to the attached applications list.
	 *
	 * @param app	The application to attach to this router.
	 */
	//添加应用程序到该路由模块
	public void addApplication(Application app) {
		if (!this.applications.containsKey(app.getAppID())) {
			this.applications.put(app.getAppID(),
					new LinkedList<Application>());
		}
		this.applications.get(app.getAppID()).add(app);
	}

	/**
	 * Returns all the applications that want to receive messages for the given
	 * application ID.
	 *
	 * @param ID	The application ID or <code>null</code> for all apps.
	 * @return		A list of all applications that want to receive the message.
	 */
	public Collection<Application> getApplications(String ID) {
		LinkedList<Application>	apps = new LinkedList<Application>();
		// Applications that match
		Collection<Application> tmp = this.applications.get(ID);
		if (tmp != null) {
			apps.addAll(tmp);
		}
		// Applications that want to look at all messages
		if (ID != null) {
			tmp = this.applications.get(null);
			if (tmp != null) {
				apps.addAll(tmp);
			}
		}
		return apps;
	}

	/**
	 * Creates a replicate of this router. The replicate has the same
	 * settings as this router but empty buffers and routing tables.
	 * @return The replicate
	 */
	public abstract MessageRouter replicate();

	/**
	 * Returns a String presentation of this router
	 * @return A String presentation of this router
	 */
	public String toString() {
		return getClass().getSimpleName() + " of " +
			this.getHost().toString() + " with " + getNrofMessages()
			+ " messages";
	}
}
