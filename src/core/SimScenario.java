/*
 * Copyright 2011 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package core;

import input.EventQueue;
import input.EventQueueHandler;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import movement.MapBasedMovement;
import movement.MovementModel;
import movement.map.SimMap;
import routing.MessageRouter;

/**
 * A simulation scenario used for getting and storing the settings of a
 * simulation run.
 */
public class SimScenario implements Serializable {

	/** a way to get a hold of this... */
	private static SimScenario myinstance=null;

	/** namespace of scenario settings ({@value})*/
	public static final String SCENARIO_NS = "Scenario";//场景的命名空间
	/** number of host groups -setting id ({@value})*/
	public static final String NROF_GROUPS_S = "nrofHostGroups";//该场景中包含几个主机组
	/** number of interface types -setting id ({@value})*/
	public static final String NROF_INTTYPES_S = "nrofInterfaceTypes";//该场景中接口类型的个数
	/** scenario name -setting id ({@value})*/
	public static final String NAME_S = "name";//该场景的名称
	/** end time -setting id ({@value})*/
	public static final String END_TIME_S = "endTime";//该场景的仿真时长
	/** update interval -setting id ({@value})*/
	public static final String UP_INT_S = "updateInterval";//场景更新间隔
	/** simulate connections -setting id ({@value})*/
    //控制是否两个节点进入彼此通信范围自动打开连接
    //true则GUI界面中节点一直在动，而且会显示节点间的连接状况 
    //如果是外部读入移动数据模式，要设为false，否则节点会一直处于开连接状态。
	public static final String SIM_CON_S = "simulateConnections";

	/** namespace for interface type settings ({@value}) *///接口的命名空间
	public static final String INTTYPE_NS = "Interface";
	/** interface type -setting id ({@value}) */
	public static final String INTTYPE_S = "type";//接口的类型
	/** interface name -setting id ({@value}) */
	public static final String INTNAME_S = "name";//接口的名字

	/** namespace for application type settings ({@value}) */
	public static final String APPTYPE_NS = "Application";//应用的明明空间
	/** application type -setting id ({@value}) */
	public static final String APPTYPE_S = "type";//应用的类型
	/** setting name for the number of applications */
	public static final String APPCOUNT_S = "nrofApplications";//应用的个数

	//以下组设置对所有组生效
	/** namespace for host group settings ({@value})*/
	public static final String GROUP_NS = "Group";//组的命名空间
	/** group id -setting id ({@value})*/
	public static final String GROUP_ID_S = "groupID";//组的标识符
	/** number of hosts in the group -setting id ({@value})*/
	public static final String NROF_HOSTS_S = "nrofHosts";//组中包含的主机个数
	/** movement model class -setting id ({@value})*/
	public static final String MOVEMENT_MODEL_S = "movementModel";//组中主机的移动模型
	/** router class -setting id ({@value})*/
	public static final String ROUTER_S = "router";//组中主机的路由算法
	/** number of interfaces in the group -setting id ({@value})*/
	public static final String NROF_INTERF_S = "nrofInterfaces";//组内主机拥有的接口个数
	/** interface name in the group -setting id ({@value})*/
	public static final String INTERFACENAME_S = "interface";//组内主机的接口
	/** application name in the group -setting id ({@value})*/
	public static final String GAPPNAME_S = "application";//组内主机的应用

	/** package where to look for movement models */
	private static final String MM_PACKAGE = "movement.";//移动模型所在的包名
	/** package where to look for router classes */
	private static final String ROUTING_PACKAGE = "routing.";//路由模型所在的包名

	/** package where to look for interface classes */
	private static final String INTTYPE_PACKAGE = "interfaces.";//接口模型所在的包名

	/** package where to look for application classes */
	private static final String APP_PACKAGE = "applications.";//应用模型所在的包名

	/** The world instance */
	private World world;
	/** List of hosts in this simulation */
	protected List<DTNHost> hosts;
	/** Name of the simulation */
	private String name;
	/** number of host groups */
	int nrofGroups;
	/** Width of the world */
	private int worldSizeX;
	/** Height of the world */
	private int worldSizeY;
	/** Largest host's radio range */
	private double maxHostRange;
	/** Simulation end time */
	private double endTime;
	/** Update interval of sim time */
	private double updateInterval;
	/** External events queue */
	private EventQueueHandler eqHandler;
	/** Should connections between hosts be simulated */
	private boolean simulateConnections;
	/** Map used for host movement (if any) */
	private SimMap simMap;

	/** Global connection event listeners */
	private List<ConnectionListener> connectionListeners;
	/** Global message event listeners */
	private List<MessageListener> messageListeners;
	/** Global movement event listeners */
	private List<MovementListener> movementListeners;
	/** Global update event listeners */
	private List<UpdateListener> updateListeners;
	/** Global application event listeners */
	private List<ApplicationListener> appListeners;

	static {
		DTNSim.registerForReset(SimScenario.class.getCanonicalName());
		reset();
	}

	public static void reset() {
		myinstance = null;
	}

	/**
	 * Creates a scenario based on Settings object.
	 */
	protected SimScenario() {
	//场景设置
		Settings s = new Settings(SCENARIO_NS);
		nrofGroups = s.getInt(NROF_GROUPS_S);

		this.name = s.valueFillString(s.getSetting(NAME_S));
		this.endTime = s.getDouble(END_TIME_S);
		this.updateInterval = s.getDouble(UP_INT_S);
		this.simulateConnections = s.getBoolean(SIM_CON_S);

		s.ensurePositiveValue(nrofGroups, NROF_GROUPS_S);
		s.ensurePositiveValue(endTime, END_TIME_S);
		s.ensurePositiveValue(updateInterval, UP_INT_S);

		this.simMap = null;
		this.maxHostRange = 1;
	//创建存放监听器的list
		this.connectionListeners = new ArrayList<ConnectionListener>();
		this.messageListeners = new ArrayList<MessageListener>();
		this.movementListeners = new ArrayList<MovementListener>();
		this.updateListeners = new ArrayList<UpdateListener>();
		this.appListeners = new ArrayList<ApplicationListener>();
	//创建事件队列处理器
		this.eqHandler = new EventQueueHandler();
	//移动模型设置
		/* TODO: check size from movement models */
		s.setNameSpace(MovementModel.MOVEMENT_MODEL_NS);
		int [] worldSize = s.getCsvInts(MovementModel.WORLD_SIZE, 2);
		this.worldSizeX = worldSize[0];
		this.worldSizeY = worldSize[1];
	//创建主机
		createHosts();

		this.world = new World(hosts, worldSizeX, worldSizeY, updateInterval,
				updateListeners, simulateConnections,
				eqHandler.getEventQueues());
	}

	/**
	 * Returns the SimScenario instance and creates one if it doesn't exist yet
	 */
	public static SimScenario getInstance() {
		if (myinstance == null) {
			myinstance = new SimScenario();
		}
		return myinstance;
	}



	/**
	 * Returns the name of the simulation run
	 * @return the name of the simulation run
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * Returns true if connections should be simulated
	 * @return true if connections should be simulated (false if not)
	 */
	public boolean simulateConnections() {
		return this.simulateConnections;
	}

	/**
	 * Returns the width of the world
	 * @return the width of the world
	 */
	public int getWorldSizeX() {
		return this.worldSizeX;
	}

	/**
	 * Returns the height of the world
	 * @return the height of the world
	 */
	public int getWorldSizeY() {
		return worldSizeY;
	}

	/**
	 * Returns simulation's end time
	 * @return simulation's end time
	 */
	public double getEndTime() {
		return endTime;
	}

	/**
	 * Returns update interval (simulated seconds) of the simulation
	 * @return update interval (simulated seconds) of the simulation
	 */
	public double getUpdateInterval() {
		return updateInterval;
	}

	/**
	 * Returns how long range the hosts' radios have
	 * @return Range in meters
	 */
	public double getMaxHostRange() {
		return maxHostRange;
	}

	/**
	 * Returns the (external) event queue(s) of this scenario or null if there
	 * aren't any
	 * @return External event queues in a list or null
	 */
	public List<EventQueue> getExternalEvents() {
		return this.eqHandler.getEventQueues();
	}

	/**
	 * Returns the SimMap this scenario uses, or null if scenario doesn't
	 * use any map
	 * @return SimMap or null if no map is used
	 */
	public SimMap getMap() {
		return this.simMap;
	}

	/**
	 * Adds a new connection listener for all nodes
	 * @param cl The listener
	 */
	public void addConnectionListener(ConnectionListener cl){
		this.connectionListeners.add(cl);
	}

	/**
	 * Adds a new message listener for all nodes
	 * @param ml The listener
	 */
	public void addMessageListener(MessageListener ml){
		this.messageListeners.add(ml);
	}

	/**
	 * Adds a new movement listener for all nodes
	 * @param ml The listener
	 */
	public void addMovementListener(MovementListener ml){
		this.movementListeners.add(ml);

		// Invoke the initialLocation() for all nodes that already exist in
		// the Scenario. This ensures initialLocation() gets called for every
		// node.
		for (final DTNHost host : this.hosts) {
			ml.initialLocation(host, host.getLocation());
		}
	}

	/**
	 * Adds a new update listener for the world
	 * @param ul The listener
	 */
	public void addUpdateListener(UpdateListener ul) {
		this.updateListeners.add(ul);
	}

	/**
	 * Returns the list of registered update listeners
	 * @return the list of registered update listeners
	 */
	public List<UpdateListener> getUpdateListeners() {
		return this.updateListeners;
	}

	/**
	 * Adds a new application event listener for all nodes.
	 * @param al The listener
	 */
	public void addApplicationListener(ApplicationListener al) {
		this.appListeners.add(al);
	}

	/**
	 * Returns the list of registered application event listeners
	 * @return the list of registered application event listeners
	 */
	public List<ApplicationListener> getApplicationListeners() {
		return this.appListeners;
	}

	/**
	 * Creates hosts for the scenario
	 */
	protected void createHosts() {
		this.hosts = new ArrayList<DTNHost>();

		for (int i=1; i<=nrofGroups; i++) {
			List<NetworkInterface> interfaces =
				new ArrayList<NetworkInterface>();
			Settings s = new Settings(GROUP_NS+i);
			s.setSecondaryNamespace(GROUP_NS);//上面两行说明nameSpace与secondaryNameSpace的字段是通用的
			String gid = s.getSetting(GROUP_ID_S);// GROUP_ID_S = "groupID"
			int nrofHosts = s.getInt(NROF_HOSTS_S);
			int nrofInterfaces = s.getInt(NROF_INTERF_S);
			int appCount;

			// creates prototypes of MessageRouter and MovementModel
		//创建移动模块对象
			MovementModel mmProto =
				(MovementModel)s.createIntializedObject(MM_PACKAGE +
						s.getSetting(MOVEMENT_MODEL_S));
		//创建路由模块对象
			MessageRouter mRouterProto =
				(MessageRouter)s.createIntializedObject(ROUTING_PACKAGE +
						s.getSetting(ROUTER_S));

			/* checks that these values are positive (throws Error if not) */
			s.ensurePositiveValue(nrofHosts, NROF_HOSTS_S);
			s.ensurePositiveValue(nrofInterfaces, NROF_INTERF_S);
		//创建接口板以及接口
			// setup interfaces
			for (int j=1;j<=nrofInterfaces;j++) {
				String intName = s.getSetting(INTERFACENAME_S + j);
				Settings intSettings = new Settings(intName);
				NetworkInterface iface =
					(NetworkInterface)intSettings.createIntializedObject(
							INTTYPE_PACKAGE +intSettings.getSetting(INTTYPE_S));
				iface.setClisteners(connectionListeners);
				iface.setGroupSettings(s);//默认设置没有关于这个的设置
				interfaces.add(iface);
			}
		//应用设置：(默认设置也没设置)
			// setup applications
			if (s.contains(APPCOUNT_S)) {// APPCOUNT_S = "nrofApplications"
				appCount = s.getInt(APPCOUNT_S);
			} else {
				appCount = 0;
			}
			for (int j=1; j<=appCount; j++) {
				String appname = null;
				Application protoApp = null;
				try {
					// Get name of the application for this group
					appname = s.getSetting(GAPPNAME_S+j);
					// Get settings for the given application
					Settings t = new Settings(appname);
					// Load an instance of the application
					protoApp = (Application)t.createIntializedObject(
							APP_PACKAGE + t.getSetting(APPTYPE_S));
					// Set application listeners
					protoApp.setAppListeners(this.appListeners);
					// Set the proto application in proto router
					//mRouterProto.setApplication(protoApp);
					mRouterProto.addApplication(protoApp);
				} catch (SettingsError se) {
					// Failed to create an application for this group
					System.err.println("Failed to setup an application: " + se);
					System.err.println("Caught at " + se.getStackTrace()[0]);
					System.exit(-1);
				}
			}
			//如果该主机的移动模型是"基于地图的移动模型"，就获取地图
			if (mmProto instanceof MapBasedMovement) {
				this.simMap = ((MapBasedMovement)mmProto).getMap();
			}

			// creates hosts of ith group
			for (int j=0; j<nrofHosts; j++) {
				ModuleCommunicationBus comBus = new ModuleCommunicationBus();

				// prototypes are given to new DTNHost which replicates
				// new instances of movement model and message router
				DTNHost host = new DTNHost(this.messageListeners,
						this.movementListeners,	gid, interfaces, comBus,
						mmProto, mRouterProto);
				hosts.add(host);
			}
		}
	}

	/**
	 * Returns the list of nodes for this scenario.
	 * @return the list of nodes for this scenario.
	 */
	public List<DTNHost> getHosts() {
		return this.hosts;
	}

	/**
	 * Returns the World object of this scenario
	 * @return the World object
	 */
	public World getWorld() {
		return this.world;
	}

}
