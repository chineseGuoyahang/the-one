/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package core;
import gui.DTNSimGUI;
import guologutils.GuoLog;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import ui.DTNSimTextUI;

/**
 * Simulator's main class
 */
public class DTNSim {
	/** If this option ({@value}) is given to program, batch mode and
	 * Text UI are used*/
	public static final String BATCH_MODE_FLAG = "-b";
	/** Delimiter for batch mode index range values (colon) */
	public static final String RANGE_DELIMETER = ":";

	/** Name of the static method that all resettable classes must have
	 * @see #registerForReset(String) */
	public static final String RESET_METHOD_NAME = "reset";
	/** List of class names that should be reset between batch runs */
	private static List<Class<?>> resetList = new ArrayList<Class<?>>();

	/**
	 * Starts the user interface with given arguments.
	 * If first argument is {@link #BATCH_MODE_FLAG}, the batch mode and text UI
	 * is started. The batch mode option must be followed by the number of runs,
	 * or a with a combination of starting run and the number of runs,
	 * delimited with a {@value #RANGE_DELIMETER}. Different settings from run
	 * arrays are used for different runs (see
	 * {@link Settings#setRunIndex(int)}). Following arguments are the settings
	 * files for the simulation run (if any). For GUI mode, the number before
	 * settings files (if given) is the run index to use for that run.
	 * @param args Command line arguments
	 */
	
	/**
	    * 命令行格式 : one.sh [ -b runcount ] [ conf-files ]
	 * -b : 表示不使用GUI模式仿真
	 * runcount : 表示运行次数
	 * conf-files : 表示用户特定的配置文件名  若省略则使用默认的配置文件default_settings.txt
	 * 
	 * @param args
	 */
	
	public static void main(String[] args) {
		boolean batchMode = false;//用于判断仿真器是以TEXT方式运行还是以GUI的方式运行
		String confFiles[];//等价于命令行后面的一大堆参数
		int firstConfIndex = 0;//第一个配置文件的下标
		int guiIndex = 0;//不知道干嘛用的，应该是和nrofRuns有着相同的作用
		int nrofRuns[] = {0,1};//暂时未从源码看出其作用，应该是和guiIndex有着相同的作用
		/**
		 * 这里的guiIndex与nrofRuns看起来像是给每个运行的进程一个名字
		 * 如开启一个GUI。这个GUI的Setting.index=guiIndex
		 */
		/* set US locale to parse decimals in consistent way */
		java.util.Locale.setDefault(java.util.Locale.US);

		if (args.length > 0) {
			if (args[0].equals(BATCH_MODE_FLAG)) {
			    /*不使用GUI模式*/
				batchMode = true;
				//one.sh  -b 这种命令行输入方式
                if (args.length == 1) {
                    firstConfIndex = 1;
                }
                else {
                   // one.sh  -b 5 [configFile] 这种命令行输入方式
                    nrofRuns = parseNrofRuns(args[1]);
                    firstConfIndex = 2;
                }
			}
			else { /* GUI mode */
				try { /* is there a run index for the GUI mode ? */
				  //one.sh  5 [configFile] 这种命令行输入方式
					guiIndex = Integer.parseInt(args[0]);
					firstConfIndex = 1;
				} catch (NumberFormatException e) {
				  //one.sh  [configFile] 这种命令行输入方式
					firstConfIndex = 0;
				}
			}
			confFiles = args;
		}
		else {
			confFiles = new String[] {null};
		}

		initSettings(confFiles, firstConfIndex);

		if (batchMode) {
			long startTime = System.currentTimeMillis();
			for (int i=nrofRuns[0]; i<nrofRuns[1]; i++) {
				print("Run " + (i+1) + "/" + nrofRuns[1]);
				Settings.setRunIndex(i);
				resetForNextRun();
				new DTNSimTextUI().start();
			}
			double duration = (System.currentTimeMillis() - startTime)/1000.0;
			print("---\nAll done in " + String.format("%.2f", duration) + "s");
		}
		else {
			Settings.setRunIndex(guiIndex);
			new DTNSimGUI().start();
		}
	}

	/**
	 * 根据confFiles与firstIndex进行加载配置文件
	 * 其中firstIndex为confFiles中第一个配置文件下标的索引
	 * 如果加载配置文件出错，那么就进行判断命令行是否把运行索引写错了位置
	 *     如果Integer.parseInt(confFiles[i])转成整数成功，那么就提示命令行中运行索引写错了位置
	 *     “运行索引只能位于第一个位置或者-b之后”
	 * Initializes Settings
	 * @param confFiles File name paths where to read additional settings
	 * @param firstIndex Index of the first config file name
	 */
	private static void initSettings(String[] confFiles, int firstIndex) {
		int i = firstIndex;
		//此处判断是判断是 否命令行参数没有写配置文件，直接返回
        if (i >= confFiles.length) {
            return;
        }

		try {
			Settings.init(confFiles[i]);
			for (i=firstIndex+1; i<confFiles.length; i++) {
				Settings.addSettings(confFiles[i]);
			}
		}
		catch (SettingsError er) {
			try {
				Integer.parseInt(confFiles[i]);
			}
			catch (NumberFormatException nfe) {
				/* was not a numeric value */
				System.err.println("Failed to load settings: " + er);
				System.err.println("Caught at " + er.getStackTrace()[0]);
				System.exit(-1);
			}
			System.err.println("Warning: using deprecated way of " +
					"expressing run indexes. Run index should be the " +
					"first option, or right after -b option (optionally " +
					"as a range of start and end values).");
			System.exit(-1);
		}
	}

	/**
	 * Registers a class for resetting. Reset is performed after every
	 * batch run of the simulator to reset the class' state to initial
	 * state. All classes that have static fields that should be resetted
	 * to initial values between the batch runs should register using
	 * this method. The given class must have a static implementation
	 * for the resetting method (a method called {@value #RESET_METHOD_NAME}
	 * without any parameters).
	 * @param className Full name (i.e., containing the packet path)
	 * of the class to register. For example: <code>core.SimClock</code>
	 */
	//假设有场景1，那么场景1的创建就会把相关类的静态变量更改
	//此时另开一个场景2，场景2与场景1相互独立，那么就需要把场景1更改的变量全部改为默认值，该方法就是把需要更改的类放入一个list
	public static void registerForReset(String className) {
		Class<?> c = null;
		try {
			c = Class.forName(className);
			c.getMethod(RESET_METHOD_NAME);
		} catch (ClassNotFoundException e) {
			System.err.println("Can't register class " + className +
					" for resetting; class not found");
			System.exit(-1);

		}
		catch (NoSuchMethodException e) {
			System.err.println("Can't register class " + className +
			" for resetting; class doesn't contain resetting method");
			System.exit(-1);
		}
		resetList.add(c);
	}

	/**
	 * Resets all registered classes.
	 */
	//把上面所讲的list进行遍历并还原默认值
	private static void resetForNextRun() {
		for (Class<?> c : resetList) {
			try {
				Method m = c.getMethod(RESET_METHOD_NAME);
				m.invoke(null);
			} catch (Exception e) {
				System.err.println("Failed to reset class " + c.getName());
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}

	/**
	 * Parses the number of runs, and an optional starting run index, from a
	 * command line argument 
	 * 
	 * 从命令行参数中解析出运行次数和可选的开始运行的索引
	 * 命令行格式 : one.sh [ -b runcount ] [ conf-files ]
	 * arg就是命令行中的runcount
	 * 此处可以看出runcount有两种写法
	 *     2:3 此时val=[2,3];
	 *     3 此时val=[0,3];
	 * 
	 * @param arg The argument to parse
	 * @return The first and (last_run_index - 1) in an array
	 */
	private static int[] parseNrofRuns(String arg) {
		int val[] = {0,1};
		try {
			if (arg.contains(RANGE_DELIMETER)) {
				val[0] = Integer.parseInt(arg.substring(0,
						arg.indexOf(RANGE_DELIMETER))) - 1;
				val[1] = Integer.parseInt(arg.substring(arg.
						indexOf(RANGE_DELIMETER) + 1, arg.length()));
			}
			else {
				val[0] = 0;
				val[1] = Integer.parseInt(arg);
			}
		} catch (NumberFormatException e) {
			System.err.println("Invalid argument '" + arg + "' for" +
					" number of runs");
			System.err.println("The argument must be either a single value, " +
					"or a range of values (e.g., '2:5'). Note that this " +
					"option has changed in version 1.3.");
			System.exit(-1);
		}

		if (val[0] < 0) {
			System.err.println("Starting run value can't be smaller than 1");
			System.exit(-1);
		}
		if (val[0] >= val[1]) {
			System.err.println("Starting run value can't be bigger than the " +
					"last run value");
			System.exit(-1);
		}

		return val;
	}

	/**
	 * Prints text to stdout
	 * @param txt Text to print
	 */
	private static void print(String txt) {
		System.out.println(txt);
	}
}
