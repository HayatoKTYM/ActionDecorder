
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Event;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Queue;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.RandomStringUtils;
import org.monea.api.LocalModuleProxy;
import org.monea.api.ModuleContext;
import org.monea.api.ModuleException;
import org.monea.api.ProcessingRequest;
import org.monea.api.ProcessingRequest.Param;
import org.monea.api.ProcessingRequestBuilder;
import org.monea.api.ProcessingRequestQueue;
import org.monea.api.RemoteModuleProxy;

public class Main  extends Frame {

	class Ad extends WindowAdapter
	{
	    public void windowClosing(WindowEvent e){
	       System.exit(0);
	    }
	}


	static int robot_id=0;
	static final String TAG = "ActionDecoder";
	static int cover_eye_range=0;

	public static HashMap<String, BasicMotion> Basic_motion_map = new HashMap<String,BasicMotion >();

	static Pattern p=Pattern.compile("(.*)\\[([^\\[]*)\\]$");
	static Pattern p2=Pattern.compile("(.+)=(.+)");
	static Pattern np = Pattern.compile("([-]?[0-9]+)");

	public static void main(String[] args) throws ModuleException, IOException, InterruptedException {
	System.setProperty("REGISTRY_SERVER_PORT","25001");

    Main f = new Main();
    f.setSize(200, 100);
    Button myb_p,myb_so,myb_sc;
    myb_p=new Button("Pepper");
    myb_so=new Button("Sota");
    myb_sc=new Button("Schema");

    f.add(myb_p, BorderLayout.EAST);
    f.add(myb_so, BorderLayout.CENTER);
    f.add(myb_sc, BorderLayout.WEST);

    f.setVisible(true);
    f.addWindowListener(f.new Ad());

    while(robot_id==0){
    	Thread.sleep(10);
    }

	RobotUtil.Log(TAG,"Monea接続待ち");
	MONEAConnector moneaConnector = MONEAConnector.getInstance("xml/ActionDecoder.xml");
	ModuleContext context = moneaConnector.getModuleContext();
	while(context == null)
		context = moneaConnector.timedGetModuleContext(-1);
	RobotUtil.Log(TAG,"Monea接続完了");
	Thread.sleep(1000);
	LocalModuleProxy localModule = context.getLocalModule();
	while(localModule == null)
    	localModule = context.getLocalModule();
	RobotUtil.Log(TAG,"ローカルモジュール取得完了");


	Basic_motion_map.put("speak", Speaking_Controller.getInstance());
	Basic_motion_map.put("led", LED_Controller.getInstance());
	Basic_motion_map.put("wait", Wait_Controller.getInstance());
	Basic_motion_map.put("head_p", Moter_Controller.getInstance("head_p"));
	Basic_motion_map.put("head_y", Moter_Controller.getInstance("head_y"));
	Basic_motion_map.put("head_r", Moter_Controller.getInstance("head_r"));
	Basic_motion_map.put("eye_p", Moter_Controller.getInstance("eye_p"));
	Basic_motion_map.put("eye_y", Moter_Controller.getInstance("eye_y"));
	Basic_motion_map.put("l_sho", Moter_Controller.getInstance("l_sho"));
	Basic_motion_map.put("r_sho", Moter_Controller.getInstance("r_sho"));
	Basic_motion_map.put("l_elb", Moter_Controller.getInstance("l_elb"));
	Basic_motion_map.put("r_elb", Moter_Controller.getInstance("r_elb"));
	Basic_motion_map.put("leg", Moter_Controller.getInstance("leg"));
	Basic_motion_map.put("body_y", Moter_Controller.getInstance("body_y"));

	RemoteModuleProxy AM = context.getRemoteModule("AM");
	final AM_Watcher am_watcher=new AM_Watcher(AM);
	am_watcher.setDaemon(true);
	RemoteModuleProxy PNS = context.getRemoteModule("PNS");
	final PNS_Watcher pns_Watcher =PNS_Watcher.getInstance(PNS);
	pns_Watcher.setDaemon(true);
	pns_Watcher.start();
	RemoteModuleProxy AP,AP_S;
	if(robot_id==1){
		AP = context.getRemoteModule("P_AP");
		AP_S = context.getRemoteModule("P_AP_S");
	}else if(robot_id==2){
		AP = context.getRemoteModule("SO_AP");
		AP_S = context.getRemoteModule("SO_AP_S");
	}else {
		AP = context.getRemoteModule("SC_AP");
		AP_S = context.getRemoteModule("SC_AP_S");
		cover_eye_range=20;
	}

	AP.timedUpdate(-1);
	AP_S.timedUpdate(-1);



	RobotUtil.Log(TAG,"Robot接続");
	new Robot(AP);
	new Robot_status(AP_S);

	Speaking_Controller speaking_Controller =Speaking_Controller.getInstance();
	am_watcher.start();

	ProcessingRequestQueue queue1 = localModule.getProcessingRequestEventQueue("play");
	ProcessingRequestQueue queue2 = localModule.getProcessingRequestEventQueue("cancel");
	final Action_Watcher action_watcher=new Action_Watcher(queue1,queue2);
	action_watcher.setDaemon(true);
	action_watcher.start();

    //シャットダウンフックを登録します。
    Runtime.getRuntime().addShutdownHook(new Thread(){
    		public void run(){
    		terminate();
    	}}
     );

    String speaked_text="";
    String recent_speaking_status="";
    String trees_graph="";
    String old_trees_graph="";

    //メインスレッドをmonea公開用に使用
	while(true){


		if(speaking_Controller.status.equals("ready") && recent_speaking_status.equals("play")){
			speaked_text=speaking_Controller.speaking_text;
			localModule.setAsString("Speaked_Content", speaked_text);
		}
		recent_speaking_status=speaking_Controller.status;

		trees_graph=makeGraph(action_watcher);
		if(! trees_graph.equals(old_trees_graph)){
			localModule.setAsString("tree", trees_graph);
			old_trees_graph=trees_graph;
		}

		localModule.setAsInt("position_x", (int)Robot.getCurrentAngle("position_x"));
		localModule.setAsInt("position_y", (int)Robot.getCurrentAngle("position_y"));
		localModule.setAsInt("body_y", Robot.getCurrentAngle("body_y"));
		localModule.commit();


		Thread.sleep(50);
	}


	}

	public synchronized static String makeGraph(Action_Watcher action_watcher){
		String trees_graph="Action<";

			for(int i=0;i<action_watcher.trees_map.size();i++){
				trees_graph += action_watcher.trees_map.get(i).treegraph();
			}

		trees_graph+=">";
		return trees_graph;
	}

	public static void terminate(){
		RobotUtil.Log(TAG, "終了処理");
	}
    public boolean action(Event e,Object o){
        if(o.equals("Pepper")){
            robot_id=1;
         }else if(o.equals("Sota")){
             //ボタンAが押されたときの処理
             robot_id=2;
          }else if(o.equals("Schema")){
        	  robot_id=3;
          }
         repaint();
         return true;
    }

    public void paint(Graphics g)
    {
    	g.drawString("ロボットを選択",50,70);
    }
}


class Robot extends Thread{
    static final String TAG = "AP_watcher";

    private static RemoteModuleProxy AP;

    private static HashMap<String,Integer> current_angle = new HashMap<String,Integer>();

   public Robot(RemoteModuleProxy AP) {
       Robot.AP=AP;
       this.setDaemon(true);
       this.start();
   }

   public void run() {
	   RobotUtil.Log(TAG,"start thread");
	   while(true){

			try {
				Robot.AP.timedUpdate(-1);

				String[] angle = Robot.AP.getAsStringArray("angle");
				if (angle!=null){
					for(int i=0;i<angle.length/2;i++){
						current_angle.put(angle[i*2].trim(),Integer.parseInt(angle[i*2+1].trim()));
					}
				}
			} catch (ModuleException | IOException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}
	   }
	}

    //Speak
	static synchronized void playSpeak(String actionName,RequestParams requestParams, int epock){
		try {
				ProcessingRequestBuilder bulder = AP.newProcessingRequestBuilder("play");
				bulder.characters("actionName", actionName);
				bulder.characters("content",requestParams.content);
				bulder.characters("duration", String.valueOf((int)requestParams.duration));
				bulder.characters("epock", String.valueOf(epock));
				bulder.sendMessage();
		} catch (ModuleException | IOException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}

	}

	static Color Eye_Color=new Color(0,0,255);
	public static double H=180.0;
	public static double S=255.0;
	public static double V=255.0;
	//LED
	static synchronized void playLED(String actionName,RequestParams requestParams, int epock){
		H = (Double)Math.abs((requestParams.points[0])%361.0);
		V = (Double)(Math.abs(requestParams.points[1]%181));
		int Hi=(int)(H/60.0);
		double F=H/60-Hi;
		double N=V*(1-F);
		double K=V*F;
		if(Hi==0){
			Eye_Color=new Color((int)V,(int)K,(int)0);
		}else if(Hi==1){
			Eye_Color=new Color((int)N,(int)V,(int)0);
		}else if(Hi==2){
			Eye_Color=new Color((int)0,(int)V,(int)K);
		}else if(Hi==3){
			Eye_Color=new Color((int)0,(int)N,(int)V);
		}else if(Hi==4){
			Eye_Color=new Color((int)K,(int)0,(int)V);
		}else{
			Eye_Color=new Color((int)V,(int)0,(int)N);
		}
		try {
			ProcessingRequestBuilder bulder = AP.newProcessingRequestBuilder("play");
			bulder.characters("actionName", actionName);
			bulder.characters("x", String.valueOf((int)Eye_Color.getBlue()));
			bulder.characters("y", String.valueOf((int)Eye_Color.getGreen()));
			bulder.characters("z", String.valueOf((int)Eye_Color.getRed()));
			bulder.characters("duration", String.valueOf((int)requestParams.duration));
			bulder.characters("epock", String.valueOf(epock));
			bulder.sendMessage();
		} catch (IOException | ModuleException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}

	}

	//Moter
	static synchronized void play(String actionName,RequestParams requestParams, int epock){

		try {
			ProcessingRequestBuilder bulder = AP.newProcessingRequestBuilder("play");
			bulder.characters("actionName", actionName);
			bulder.characters("x", String.valueOf(requestParams.angle.intValue()));
			bulder.characters("y", String.valueOf((int)(requestParams.points[1])));
			bulder.characters("z", String.valueOf((int)(requestParams.points[2])));
			bulder.characters("duration", String.valueOf((int)requestParams.duration));
			bulder.characters("epock", String.valueOf(epock));
			bulder.sendMessage();
		} catch (IOException | ModuleException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}

	}


	static synchronized void stop(String actionName){
		try {
			ProcessingRequestBuilder bulder = AP.newProcessingRequestBuilder("cancel");
			bulder.characters("actionName", actionName);
			bulder.sendMessage();
		} catch (IOException | ModuleException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
	}

	static synchronized int getCurrentAngle(String actionName){
		if(current_angle.containsKey(actionName) && current_angle.get(actionName)!=null){
			return current_angle.get(actionName);
		}else{
			return 0;
		}
	}


}

class Robot_status extends Thread{
    static final String TAG = "AP_S_watcher";
    private static RemoteModuleProxy AP_S;

    private static HashMap<String,String> statuses = new HashMap<String,String>();


   public Robot_status(RemoteModuleProxy AP_S) {
       Robot_status.AP_S=AP_S;
       this.setDaemon(true);
       this.start();
   }

   public void run() {
	   RobotUtil.Log(TAG,"start thread");
	   String status="unkown";
	   String actionName="";
	   while(true){

			try {
				Robot_status.AP_S.timedUpdate(-1);

				String[] status_row = Robot_status.AP_S.getAsStringArray("status");
				if (status_row!=null){
					for(int i=0;i<status_row.length/2;i++){
						actionName=status_row[i*2].trim();
						status=status_row[i*2+1].trim().split(":")[0];
						//epockのチェック
						if (status_row[i*2+1].trim().split(":").length!=2 || Integer.valueOf(status_row[i*2+1].trim().split(":")[1]) < Main.Basic_motion_map.get(actionName).epock){
							continue;
						}
						Main.Basic_motion_map.get(actionName).updataStatus(status);
						//RobotUtil.Log(TAG,status_row[i*2].trim()+" "+status_row[i*2+1].trim());
				}
				}
			} catch (ModuleException | IOException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}
	   }
	}

	static synchronized String getCurrentStatus(String actionName){
		if(statuses.containsKey(actionName) && statuses.get(actionName)!=null){
			return statuses.get(actionName);
		}else{
			return "unknown";
		}
	}

}



class AM_Watcher extends Thread{
    static final String TAG = "AM_watcher";

	   RemoteModuleProxy AM;
	   public static HashMap<String,Integer> priority_map = new HashMap<String,Integer>();


	   public AM_Watcher(RemoteModuleProxy AM) {
	       this.AM=AM;
			priority_map.put("A", 2);
			priority_map.put("B", 1);
			priority_map.put("C", 0);
			priority_map.put("root", Integer.MAX_VALUE);

	   }

	   public void run() {

		   //初期化
			Main.Basic_motion_map.forEach(new BiConsumer<String, BasicMotion>(){
			@Override
		    public void accept(String actionName, BasicMotion basicMotion) {
		        basicMotion.update_priority_callback(priority_map);
		    }});

			while(true){
			try {
				AM.timedUpdate(-1);
				priority_map.put("A", AM.getAsInt("A"));
				priority_map.put("B", AM.getAsInt("B"));
				priority_map.put("C", AM.getAsInt("C"));
				RobotUtil.Log(TAG,"priprity_map"+" A:"+priority_map.get("A")+" B:"+priority_map.get("B")+" C:"+priority_map.get("C"));

				Main.Basic_motion_map.forEach(new BiConsumer<String, BasicMotion>(){
				@Override
			    public void accept(String actionName, BasicMotion basicMotion) {
			        basicMotion.update_priority_callback(priority_map);
			    }});

			} catch (ModuleException | IOException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}
			}
		   }

	  }

class PNS_Watcher extends Thread{

	   Queue<String> targets = new ArrayDeque<String>();
	   static String TAG="PNS";
	   RemoteModuleProxy PNS;
	   boolean anychangeFlag=false;

	   private static PNS_Watcher instance ;

	   private HashMap<Action_Node,String> callback_map =new  HashMap<Action_Node,String>();
	   HashMap<String,PersonPosition> person_map =new  HashMap<String,PersonPosition>();

	   public PNS_Watcher(RemoteModuleProxy PNS) {
		    this.PNS=PNS;
	   }

		//シングルトン化
		public static PNS_Watcher getInstance() {
			if (instance != null){
				return instance;
			}
			RobotUtil.Log(TAG,"インスタンスが生成されていない");
			return null;
		}

		//シングルトン化
		public static PNS_Watcher getInstance(RemoteModuleProxy S_PNS) {
			if (instance != null){
				return instance;
			}
			instance = new PNS_Watcher(S_PNS);
			return instance;
		}

		class PersonPosition{
			public String name="";
			public double[] points =new double[]{0,0,0};
			public boolean changeFlag=false;
			public boolean updataFlag=false;

			public void setInfo(String[] info){
				updataFlag=true;
				name=info[0];
				//変化量が10cm以上なら
				if(Math.abs(points[0] - Double.parseDouble(info[1]))>10 || Math.abs(points[1] - Double.parseDouble(info[2]))>10){
					changeFlag=true;
					anychangeFlag=true;
				}else{
				}
				if(changeFlag){
					points[0]=Double.parseDouble(info[1]);
					points[1]=Double.parseDouble(info[2]);
				}
			}
		}

		public ArrayList<Action_Node> dead_node_map = new ArrayList<Action_Node>();

	   public void run() {
			RobotUtil.Log(TAG,"起動");
		   while(true){
			try {
				PNS.timedUpdate(-1);
				String[] position = PNS.getAsStringArray("positions2");
				if(position==null){
					continue;
				}
				for(int i=0;i<position.length;i++){
					String[] person_info = position[i].split(",");
					if(person_info.length==3){
						if(person_map.containsKey(person_info[0])){
							person_map.get(person_info[0]).setInfo(person_info);
						}else{
							PersonPosition personPosition=new PersonPosition();
							personPosition.setInfo(person_info);
							person_map.put(person_info[0], personPosition);
						}
					}
				}


				String[] array=(String[])person_map.keySet().toArray(new String[0]);
				for(int i=0;i<array.length;i++){
					if(! person_map.get(array[i]).updataFlag){
						person_map.remove(array[i]);
					}
				}


				dead_node_map.clear();
				callback_map.forEach(new BiConsumer<Action_Node, String>(){
			    public void accept(Action_Node action_node,String target) {
		    		if(action_node == null || action_node.isWither){
		    			dead_node_map.add(action_node);
		    		}else if(person_map.containsKey(target)){
		    			if(person_map.get(target).changeFlag){
				    	//if(anychangeFlag){ //ある節の運動変化が別の節の運動位置に影響を与えうるので仕方なく全再起動
				    		RobotUtil.Log(TAG,target);
				    		action_node.update_position_callback(person_map.get(target).points);
				    	}
		    		 }
			    }});


				//枯れた節を表から削除
				ListIterator<Action_Node> dead_node_map_ite = dead_node_map.listIterator();
				while(dead_node_map_ite.hasNext()){
					callback_map.remove(dead_node_map_ite.next());
				}

				person_map.forEach(new BiConsumer<String,PersonPosition>(){
				    public void accept(String target,PersonPosition personPosition) {
				    	personPosition.changeFlag=false;
				    	personPosition.updataFlag=false;
				}});
				anychangeFlag=false;


			} catch (IOException e) {
				// TODO 自動生成された catch ブロック
				RobotUtil.Log(TAG,"PNS don't work");
				//e.printStackTrace();
			} catch (ModuleException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}
	   }
	   }


	   public synchronized void setCallback(Action_Node action_node, String target) {
		   callback_map.put(action_node, target);
		   RobotUtil.Log(TAG,"aaaaaaaaaa: "+target);
		   if(person_map.containsKey(target)){
			   action_node.update_position_callback(person_map.get(target).points);
		   }
		}


	   public synchronized void removeCallback(Action_Node action_node){
		   if(callback_map.containsKey(action_node)){
			   callback_map.remove(action_node);
		   }
	   }
}

class Action_Watcher extends Thread implements Request_Watcher{
    static final String TAG = "Action_watcher";
	   ProcessingRequestQueue play_queue=null;
	   ProcessingRequestQueue cancel_queue=null;
	   public ArrayList<Action_Tree> trees_map = new ArrayList<Action_Tree>();




	   public Action_Watcher(ProcessingRequestQueue play_queue,ProcessingRequestQueue cancel_queue) throws ModuleException, IOException, InterruptedException {
	       this.play_queue = play_queue;
	       this.cancel_queue = cancel_queue;
	   }

	   public void run() {

		   try {

		   ProcessingRequest item=null;
		   List<Param> params=null;
		   Param param =null;

		   while(true){

					if(! play_queue.isEmpty() || !cancel_queue.isEmpty()){
						RequestParams requestParams=new RequestParams("root");
						if(!play_queue.isEmpty()){
							item = play_queue.pop();
						}else{
							item = cancel_queue.pop();
						}
						try {
							params = item.params();
						} catch (IOException e) {
							// TODO 自動生成された catch ブロック
							e.printStackTrace();
						}
						//RobotUtil.Log(TAG, "Queue:"+item.getProcessingName());

						if (item.getProcessingName().equals("play")){
							for(int i=0;i<params.size();i++){
								param = params.get(i);
								if(param.name().equals("x")){
									requestParams.points[0]=(double)param.toFloat64();
								}else if(param.name().equals("y")){
									requestParams.points[1]=(double)param.toFloat64();
								}else if(param.name().equals("z")){
									requestParams.points[2]=(double)param.toFloat64();
								}else if (param.name().equals("content")){
									requestParams.content=param.toString();
								}else if(param.name().equals("keep")){
									requestParams.keep=param.toInteger32();
								}else if(param.name().equals("autoend")){
									requestParams.autoend=param.toInteger32();
								}else if(param.name().equals("layerName")){
									requestParams.layerName=param.toString();
								}else if(param.name().equals("target")){
									requestParams.target=param.toString();
								}else if(param.name().equals("duration")){
									requestParams.duration=(double)param.toFloat64();
								}else if(param.name().equals("actionName")){
									requestParams.actionName=param.toString();
								}
							}
							if(requestParams.actionName.equals("")){
								continue;
							}
							RobotUtil.Log(TAG, "play Que actionName:"+requestParams.actionName+" layear:"+requestParams.layerName+" taget:"+requestParams.target);
							Action_Tree action_Tree=new Action_Tree(requestParams);
							action_Tree.setDaemon(true);
							action_Tree.start();
							trees_map.add(action_Tree);

						}else if(item.getProcessingName().equals("cancel")){
							for(int i=0;i<params.size();i++){
								param = params.get(i);
								if(param.name().equals("actionName")){
									requestParams.actionName=param.toString();
								}else if(param.name().equals("layerName")){
									requestParams.layerName=param.toString();
								}
							}
							if(requestParams.actionName.equals("")){
								continue;
							}
							RobotUtil.Log(TAG, "cancel actionName:"+requestParams.actionName+" layear:"+requestParams.layerName);
							if(requestParams.actionName.equals("all")){ //コマンド名でなく全てでcancel
								Iterator<Action_Tree> trees = trees_map.iterator();
								while(trees.hasNext()){
									Action_Tree tree = trees.next();
									//if(tree.requestParams.layerName.equals(requestParams.layerName)){
										tree.cancel(tree.requestParams);
										tree.kill();
									//}
								}
							}else{
								Iterator<Action_Tree> trees = trees_map.iterator();
								while(trees.hasNext()){
									Action_Tree tree = trees.next();
									if(tree.requestParams.layerName.equals(requestParams.layerName) && tree.requestParams.actionName.equals(requestParams.actionName)){
										tree.cancel(tree.requestParams);
										tree.kill();
									}
								}
							}
						}

					}

					CheckTrees();
					Thread.sleep(10);

			}
	   		}catch (ModuleException |IOException| InterruptedException  e) {
				e.printStackTrace();
			}

	   }

	   ArrayList<Action_Tree> deactivate_trees_map = new ArrayList<Action_Tree>();
	   //活性度が負なら伐採する
	   public void update_priority_callback( HashMap<String,Integer> priority_map){
		   deactivate_trees_map.clear();
		   for(int i=0;i<trees_map.size();i++){
			  if( priority_map.get(trees_map.get(i).requestParams.layerName)<0){
				  deactivate_trees_map.add(trees_map.get(i));
			  }
		   }
		   for(int i=0;i<deactivate_trees_map.size();i++){
			 trees_map.remove(deactivate_trees_map.get(i));
			 deactivate_trees_map.get(i).kill();
		   }
	   }

	   ArrayList<Action_Tree> dead_trees_map = new ArrayList<Action_Tree>();
	   private void CheckTrees(){
		   dead_trees_map.clear();
		   for(int i=0;i<trees_map.size();i++){
			  if( ! trees_map.get(i).isAlive()){
				  dead_trees_map.add(trees_map.get(i));
			  }
		   }
		   for(int i=0;i<dead_trees_map.size();i++){
			 trees_map.remove(dead_trees_map.get(i));
		   }
	   }


}

class Action_Tree extends Thread {
		static final String TAG = "Action_Tree";
		private Action_Node action_Node = new Action_Node();
		final String UniqueName=RandomStringUtils.randomAlphanumeric(6);

	   private boolean isRunnable=true;

	   RequestParams requestParams;

	   PNS_Watcher pns_Watcher=PNS_Watcher.getInstance();

	   public Action_Tree(RequestParams requestParams){
		   this.requestParams=requestParams;
		   RobotUtil.Log(TAG,"Treeが生成:"+this.requestParams.actionName);
	   }

	   public void run() {
		   RequestParams next_requestParams = new RequestParams(UniqueName);
		   next_requestParams.copy(requestParams);
		   action_Node.build(next_requestParams,0);
		   if(next_requestParams.target.length()>0){
			   pns_Watcher.setCallback(this.action_Node, next_requestParams.target);
		   }
		   action_Node.play(0);

		   while(isRunnable){

				if(action_Node.isFinished(null)){
				   if(requestParams.autoend==1){
					   RobotUtil.Log(TAG,"Treeが実行完了:"+requestParams.actionName);
					   action_Node.cancel(null);
				   }
			   }
			   if(action_Node.isWither(requestParams)){
					   RobotUtil.Log(TAG,"Treeが枯れた:"+requestParams.actionName);
					   return;
			   }

			   try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}

		   }
		   pns_Watcher.removeCallback(action_Node);
		   action_Node.cancel(null);
		}

	   public String treegraph(){
		   if(action_Node!=null){
			   return action_Node.node_graph(requestParams);
		   }
		   return "";
	   }

	   public void kill(){
		   action_Node.kill();
		   RobotUtil.Log(TAG,"Treeを伐採:"+requestParams.actionName);
		   isRunnable=false;
	   }

	   public void cancel(RequestParams requestParams){
		   action_Node.cancel(requestParams);
	   }

}



class Action_Node implements BasicNode{
    static final String TAG = "Action_Node";
       public final String UniqueName=RandomStringUtils.randomAlphanumeric(6);

	   RequestParams requestParams;

	   private double temp_num = 0; //sequenceを跨いで変数を共有したい時に

	   private HashMap<BasicNode,RequestParams> node_requestMap =new  HashMap<BasicNode,RequestParams>();

	   static PNS_Watcher pns_Watcher=PNS_Watcher.getInstance();

	   private ArrayList<Action_Node> nodes;
	   public int sequence=0;
	   int all_sequence=0;

	   ActionRecipe actionRecipe=ActionRecipe.getInstance();
	   Recipe recipe;

	   public Action_Node(){

	   }

	   public void build(RequestParams requestParams, int sequence){
		   this.sequence=sequence;
		   this.requestParams=requestParams;
		   recipe=actionRecipe.getRecipe(requestParams.actionName);

		   if(recipe!=null){
			   all_sequence=recipe.actions.size()-1;
			   nodes = new ArrayList<Action_Node>();
			   for(int i=0;i<recipe.getNodenum(this.sequence);i++){
				   nodes.add(new Action_Node());
			   }

			   //RobotUtil.Log(TAG,"幹が生成:"+requestParams.actionName);
		   }else if(Main.Basic_motion_map.containsKey(this.requestParams.actionName)){
				   //RobotUtil.Log(TAG,"葉が生成:"+requestParams.actionName);
		   }else{
			   RobotUtil.Log(TAG,"幹の生成に失敗:"+requestParams.actionName);
		   }
	   }

		public static void setParam(RequestParams  requestParams,String args, int order_type){

				String[] sikis = args.split(",");
				for(int i=0;i<sikis.length;i++){
					String siki=sikis[i];
					Matcher m2 = Main.p2.matcher(siki); //＝を含む場合
					if(m2.find()){
						int num=0;
						Matcher m3 =Main.np.matcher(m2.group(2));
						if(m3.find()){ //数字の場合
							num=Integer.parseInt(m3.group(1));
							if(m2.group(1).equals("x")){
								requestParams.points[0]=num;
							}else if(m2.group(1).equals("y")){
								requestParams.points[1]=num;
							}else if(m2.group(1).equals("z")){
								requestParams.points[2]=num;
							}else if(m2.group(1).equals("d")){
								requestParams.duration=num;
							}else if(m2.group(1).equals("k")){
								requestParams.keep=num;
							}
						}else{
							if(order_type==0 && m2.group(1).equals("t")){
							  requestParams.target=m2.group(2);
							  requestParams.changeTarget=true;
							}
						}
					}else{
						if(siki.length()>0){
							requestParams.content=siki;
						}
					}

			}
		}

	   void update_position_callback(double[] points){
		   if(requestParams!=null){
			   requestParams.points[0]=(double)points[0]; //値渡し
			   requestParams.points[1]=(double)points[1];
			   requestParams.points[2]=(double)points[2];
			   play(1);
		   }
	   }


	   //order_type: {0:ユーザーによる1次実行, 1:PNSによる2次起動, 2:親ノードによる3次起動}
	   public synchronized void play(int oreder_Type){

		   				if(oreder_Type == 0){// ユーザーの直接実行
		   					requestParams.fromUser=true;
		   				}else if(oreder_Type == 2){ //(PNSによって活性化した)親ノードによる間接起動
		   					if(requestParams.changeTarget){

		   						return;
		   					}
		   				}

		   				if(Main.Basic_motion_map.containsKey(requestParams.actionName)){
		   					if(requestParams.actionName.equals("speak") || requestParams.actionName.equals("wait")){
		   						if(oreder_Type != 0){
		   							return;
		   						}

		   					}
		   					BasicMotion motion_instance = Main.Basic_motion_map.get(requestParams.actionName);
							node_requestMap.put(motion_instance, new RequestParams(UniqueName));
							node_requestMap.get(motion_instance).copy(requestParams);
							node_requestMap.get(motion_instance).angle=(double)requestParams.points[0];
							motion_instance.setQueue(node_requestMap.get(motion_instance));
							return;
		   				}


						///////////以下、複合行動系

		   				if(nodes==null){
		   					RobotUtil.Log(TAG,"幹が生成されていない"+requestParams.layerName+":"+requestParams.actionName);
		   					return;
		   				}

		   				if(recipe==null){
		   					RobotUtil.Log(TAG,"レシピがない"+requestParams.layerName+":"+requestParams.actionName);
		   					return;
		   				}

						for(int i=0;i<recipe.getNodenum(sequence);i++){

							if(! node_requestMap.containsKey(nodes.get(i))){ //新規ノード生成
								node_requestMap.put(nodes.get(i), new RequestParams(UniqueName));
								node_requestMap.get(nodes.get(i)).copy(requestParams);
								node_requestMap.get(nodes.get(i)).actionName=recipe.actions.get(sequence)[i];
								setParam(node_requestMap.get(nodes.get(i)), recipe.args.get(sequence)[i], oreder_Type);
								nodes.get(i).build(node_requestMap.get(nodes.get(i)),0);
							}else{ //パラメータ上書きのみ
								node_requestMap.get(nodes.get(i)).copy(requestParams);
								node_requestMap.get(nodes.get(i)).actionName=recipe.actions.get(sequence)[i];
								setParam(node_requestMap.get(nodes.get(i)), recipe.args.get(sequence)[i], oreder_Type);
								nodes.get(i).requestParams=node_requestMap.get(nodes.get(i));
							}
						}


						if(requestParams.actionName.equals("le")){
							double current_body_y = Robot.getCurrentAngle("body_y");
							double current_head_y = Robot.getCurrentAngle("head_y");
							double eye_y= (requestParams.points[0]-current_body_y-current_head_y) ;
							double current_head_p = Robot.getCurrentAngle("head_p");
							double eye_p= (requestParams.points[1]-current_head_p) ;
							node_requestMap.get(nodes.get(0)).points[0]=eye_y;
							node_requestMap.get(nodes.get(1)).points[0]=eye_p;
						}
						else if(requestParams.actionName.equals("nod")){
							if(sequence==0){
								double current_head_p = Robot.getCurrentAngle("head_p");
								temp_num=current_head_p;
								node_requestMap.get(nodes.get(0)).points[0]=-(double)10+temp_num;
							}else{
								node_requestMap.get(nodes.get(0)).points[0]=temp_num;
							}
						}
						else if(requestParams.actionName.equals("hi")){
							if(sequence==0){
								node_requestMap.get(nodes.get(0)).points[0]=40;
								node_requestMap.get(nodes.get(1)).points[0]=-40;
							}else if(sequence==1){
								node_requestMap.get(nodes.get(0)).points[0]=500;
							}else if(sequence==2){
								node_requestMap.get(nodes.get(0)).points[0]=-90;
								node_requestMap.get(nodes.get(1)).points[0]=0;
							}
						}

						else if(requestParams.actionName.equals("blink")){
							if(sequence==0){
								node_requestMap.get(nodes.get(0)).points[1]=0;
							}else if(sequence==1){
								node_requestMap.get(nodes.get(0)).points[0]=5;
							}else if(sequence==2){
								node_requestMap.get(nodes.get(0)).points[1]=180;
							}
						}
						else if(requestParams.actionName.equals("home")){
							for(int i=0;i< recipe.actions.get(sequence).length;i++){
								if(node_requestMap.get(nodes.get(i)).actionName.equals("r_sho") || node_requestMap.get(nodes.get(i)).actionName.equals("l_sho")){
									node_requestMap.get(nodes.get(i)).points[0]=-90;
								}else{
									node_requestMap.get(nodes.get(i)).points[0]=0;
								}
							}
						}

						else if(requestParams.actionName.equals("shake_left_hand") || requestParams.actionName.equals("shake_right_hand")){
							if(sequence==0){
								node_requestMap.get(nodes.get(0)).points[0]=40;
								node_requestMap.get(nodes.get(1)).points[0]=0;
							}else if(sequence==1){
								node_requestMap.get(nodes.get(0)).points[0]=2000;
							}else if(sequence==2){
								node_requestMap.get(nodes.get(0)).points[0]=-90;
								node_requestMap.get(nodes.get(1)).points[0]=0;
							}
						}
						else if(requestParams.actionName.equals("shake_head")){
							if(sequence==0){
								double current_head_y = Robot.getCurrentAngle("head_y");
								temp_num=current_head_y;
								node_requestMap.get(nodes.get(0)).points[0]=temp_num+20;
							}else if(sequence==1){
								node_requestMap.get(nodes.get(0)).points[0]=temp_num-20;
							}else if(sequence==2){
								node_requestMap.get(nodes.get(0)).points[0]=temp_num;
							}
						}
						else if(requestParams.actionName.equals("cock")){
							if(sequence==0){
								double current_head_r = Robot.getCurrentAngle("head_r");
								temp_num=current_head_r;
								node_requestMap.get(nodes.get(0)).points[0]=temp_num+30;
							}else if(sequence==1){
								node_requestMap.get(nodes.get(0)).points[0]=temp_num;
							}
						}
						else if(requestParams.actionName.equals("ln")){
							double current_body_y = Robot.getCurrentAngle("body_y");
							double current_head_y = Robot.getCurrentAngle("head_y");

							double move_y= ((double)requestParams.points[0]-current_head_y-current_body_y) ;
							double eye_y=(double)move_y;
							double head_y=(double)current_head_y;

							if(Math.abs((move_y))>Main.cover_eye_range){
								eye_y=(Math.signum((double)move_y)*Main.cover_eye_range);
								head_y=(Math.signum((double)move_y)*(Math.abs((move_y))-Main.cover_eye_range)+current_head_y);
							}

							double current_body_p = Robot.getCurrentAngle("body_p");
							double current_head_p = Robot.getCurrentAngle("head_p");

							double move_p= ((double)requestParams.points[1]-current_head_p-current_body_p) ;
							double eye_p=(double)move_p;
							double head_p=(double)current_head_p;

							if(Math.abs((move_p))>Main.cover_eye_range){
								eye_p=(Math.signum((double)move_p)*Main.cover_eye_range);
								head_p=(Math.signum((double)move_p)*(Math.abs((move_p))-Main.cover_eye_range)+current_head_p);
							}

							node_requestMap.get(nodes.get(0)).points[0]=(double)eye_y+current_head_y+current_body_y; //絶対量
							node_requestMap.get(nodes.get(0)).points[1]=(double)eye_p+current_head_p+current_body_p; //絶対量
							node_requestMap.get(nodes.get(1)).points[0]=(double)head_y;
							node_requestMap.get(nodes.get(2)).points[0]=(double)head_p;
						}
						else if(requestParams.actionName.equals("relln")){
							double current_body_y = Robot.getCurrentAngle("body_y");
							double current_head_y = Robot.getCurrentAngle("head_y");

							double move_y= ((double)requestParams.points[0]) ;
							double eye_y=(double)move_y;
							double head_y=(double)current_head_y;

							if(Math.abs((move_y))>Main.cover_eye_range){
								eye_y=(Math.signum((double)move_y)*Main.cover_eye_range);
								head_y=(Math.signum((double)move_y)*(Math.abs((move_y))-Main.cover_eye_range)+current_head_y);
							}

							double current_body_p = Robot.getCurrentAngle("body_p");
							double current_head_p = Robot.getCurrentAngle("head_p");

							double move_p= ((double)requestParams.points[1]) ;
							double eye_p=(double)move_p;
							double head_p=(double)current_head_p;

							if(Math.abs((move_p))>Main.cover_eye_range){
								eye_p=(Math.signum((double)move_p)*Main.cover_eye_range);
								head_p=(Math.signum((double)move_p)*(Math.abs((move_p))-Main.cover_eye_range)+current_head_p);
							}

							node_requestMap.get(nodes.get(0)).points[0]=(double)eye_y+current_head_y+current_body_y; //絶対量
							node_requestMap.get(nodes.get(0)).points[1]=(double)eye_p+current_head_p+current_body_p; //絶対量
							node_requestMap.get(nodes.get(1)).points[0]=(double)head_y;
							node_requestMap.get(nodes.get(2)).points[0]=(double)head_p;
						}
						else if(requestParams.actionName.equals("lt")){
							double current_body_y = Robot.getCurrentAngle("body_y");

							double move_y= ((double)requestParams.points[0]-current_body_y) ;
							double head_y=(double)move_y;
							double body_y=(double)current_body_y;

							if(Math.abs((move_y))>50){
								head_y=(Math.signum((double)move_y)*50);
								body_y=(Math.signum((double)move_y)*(Math.abs((move_y))-50)+current_body_y);
							}
							node_requestMap.get(nodes.get(0)).points[0]=(double)head_y+current_body_y; //絶対量
							node_requestMap.get(nodes.get(1)).points[0]=(double)body_y;
						}
						else if(requestParams.actionName.equals("induction")){
							if(Main.robot_id == 2){ //Sota
								if(requestParams.points[0]<0){
									if(sequence==0){
										node_requestMap.get(nodes.get(0)).points[0]=-30;
										node_requestMap.get(nodes.get(1)).points[0]=0;
										node_requestMap.get(nodes.get(2)).points[0]=-45;
										node_requestMap.get(nodes.get(3)).points[0]=-90;
									}else if(sequence==1){
										node_requestMap.get(nodes.get(0)).points[0]=-30;
										node_requestMap.get(nodes.get(1)).points[0]=0;
										node_requestMap.get(nodes.get(2)).points[0]=-90;
										node_requestMap.get(nodes.get(3)).points[0]=0;
									}
								}else{
									if(sequence==0){
										node_requestMap.get(nodes.get(2)).points[0]=-30;
										node_requestMap.get(nodes.get(3)).points[0]=0;
										node_requestMap.get(nodes.get(0)).points[0]=-45;
										node_requestMap.get(nodes.get(1)).points[0]=-90;
									}else if(sequence==1){
										node_requestMap.get(nodes.get(2)).points[0]=-30;
										node_requestMap.get(nodes.get(3)).points[0]=0;
										node_requestMap.get(nodes.get(0)).points[0]=-90;
										node_requestMap.get(nodes.get(1)).points[0]=0;
									}
								}
							}
						}

						//全てのノードを実行
						for(int i=0;i<recipe.getNodenum(sequence);i++){
							if(oreder_Type==1){ //起点がPNSによる2次的起動なら
								nodes.get(i).play(oreder_Type+1); //3次的起動であることを伝える
							}else{
								if(oreder_Type==0 && node_requestMap.get(nodes.get(i)).changeTarget){
									pns_Watcher.setCallback(nodes.get(i), node_requestMap.get(nodes.get(i)).target);
								}
								nodes.get(i).play(oreder_Type);
							}
						}
					return;

			}

	   public void cancel(RequestParams requestParams){
				   Iterator<BasicNode> node_ite = node_requestMap.keySet().iterator();
				   while(node_ite.hasNext()){
					  BasicNode node = node_ite.next();
					  node.cancel(node_requestMap.get(node));
				   }
				return;
	   }

	   @Override
	   public void kill(){
		   Iterator<BasicNode> node_ite = node_requestMap.keySet().iterator();
		   while(node_ite.hasNext()){
			  BasicNode node = node_ite.next();
			  node.kill();
		   }
		   isWither=true;
		   return;
	   }

	   private Queue<BasicNode> witherList =new ArrayDeque<BasicNode>() ;
	   boolean isWither =false;

	   //幹が全部枯れたらtrue
	   @Override
		public  synchronized boolean isWither(RequestParams requestParams){
		   Iterator<BasicNode> node_ite = node_requestMap.keySet().iterator();
		   witherList.clear();
		   boolean isFinish=true;

		   while(node_ite.hasNext()){
			  BasicNode node = node_ite.next();
			  if(! node.isWither(node_requestMap.get(node))){
				  //RobotUtil.Log(TAG, "死んでない: "+node_requestMap.get(node).actionName);
				  isFinish=false;
			  }else{
				witherList.add(node);
			  }
		   }

    	   //iterator内で削除するとエラーが起きるのでここで幹を落とす
		   while(!witherList.isEmpty()){
			   BasicNode item = witherList.poll();
			   item.kill();
			   node_requestMap.remove(item);
		   }

		   if(isFinish){
				if(sequence==all_sequence){
					RobotUtil.Log(TAG,requestParams.actionName+" 全シーケンス完了");
					isWither=true;
					return true;
				}else{
					RobotUtil.Log(TAG,requestParams.actionName+" シーケンス "+Integer.toString(sequence)+" 完了");
					 sequence++;
					 build(requestParams, sequence);
					 play(0);
				}

		   }

		   return false;
		}


	   //幹が実行完了したらtrue
	   @Override
		public boolean isFinished(RequestParams requestParams){
		   Iterator<BasicNode> node_ite = node_requestMap.keySet().iterator();
		   boolean isFinished=true;
		   while(node_ite.hasNext()){
			  BasicNode node = node_ite.next();
			  if(! node.isFinished(node_requestMap.get(node))){
				  isFinished=false;
			  }
		   }
		   if(isFinished){
		   if(sequence==all_sequence){
		    return true;
			   }else{
				 cancel(requestParams);
			   }
		   }
		   return false;

		}

	   public synchronized String node_graph(RequestParams requestParams){
		   Iterator<BasicNode> node_ite = node_requestMap.keySet().iterator();
		   String node_graph=requestParams.actionName+"<";
		   while(node_ite.hasNext()){
			  BasicNode node = node_ite.next();
			  node_graph +=node.node_graph(node_requestMap.get(node));
		   }
		   return node_graph+">";
	   }

	   }


class Moter_Controller extends BasicMotion{

	   private static HashMap<String, Moter_Controller> instanceMap =new HashMap<String, Moter_Controller>();

		String key;

		public Moter_Controller(String key) {
				this.key=key;
				TAG="Moter controller:"+key;
				this.setDaemon(true);
				this.start();
		}

		//シングルトン化
		public static Moter_Controller getInstance(String key) {
			Moter_Controller instance = instanceMap.get(key);
			if (instance != null)
				return instance;
			instance = new Moter_Controller(key);
			instanceMap.put(key, instance);
			return instance;
		}


		@Override
		public void updataStatus(String status){
			if(this.status.equals("play")){
				if(status.equals("ready")){
					this.status="ready";
					excuted_item=excuting_item;
					excuting_item=null;
					if(excuted_item.fromUser){
					 RobotUtil.Log(TAG,"実行完了:"+excuted_item.actionName);
					}
				}
				}
			}

		@Override
		public boolean play(RequestParams requestParams){
			//一部の軸を指定して動作
			//CSotaMotionの定数を利用してID指定する場合
			Robot.play(key,requestParams, epock);
			return true;
		}

	}

class Speaking_Controller extends BasicMotion{
	   private static Speaking_Controller instance ;


	   public String speaking_text="";

		public Speaking_Controller() {
				TAG="Speaking controller";
				this.setDaemon(true);
				this.start();
		}

		//シングルトン化
		public static Speaking_Controller getInstance() {
			if (instance != null)
				return instance;
			instance = new Speaking_Controller();
			return instance;
		}

		int last_status=0;



		@Override
		public void updataStatus(String status){
			if(this.status.equals("play")){
				if(status.equals("ready")){
					this.status="ready";
					excuted_item=excuting_item;
					excuting_item=null;
					remove_keep_map(excuted_item.layerName); //発話行為は再実行しない。
					RobotUtil.Log(TAG,"実行完了:"+excuted_item.actionName);
				}
				}
			}



		@Override
		public boolean play(RequestParams requestParams){
			if(requestParams.content.length()>0){
				if (this.status.equals("play")){
					//actionStop(requestParams);
				}
				Robot.playSpeak("speak",requestParams, epock);
				speaking_text=requestParams.content;
				return true;
			}

			return false;
		}

		@Override
		public void actionStop(RequestParams requestParams) {
			speaking_text="途中終了:"+speaking_text;
			RobotUtil.Log(TAG,"発話中止:"+speaking_text);
			Robot.stop("speak");

		}

	}


class LED_Controller extends BasicMotion implements Request_Watcher{

	   private static LED_Controller instance ;

		public LED_Controller() {
				TAG="LED controller";
				this.setDaemon(true);
				this.start();
		}

		//シングルトン化
		public static LED_Controller getInstance() {
			if (instance != null)
				return instance;
			instance = new LED_Controller();
			return instance;
		}

		@Override
		public void updataStatus(String status){
			if(this.status.equals("play")){
				if(status.equals("ready")){
					this.status="ready";
					excuted_item=excuting_item;
					excuting_item=null;
					RobotUtil.Log(TAG,"実行完了:"+excuted_item.actionName);
				}
				}
			}


		@Override
		public boolean play(RequestParams requestParams){

			Robot.playLED("led",requestParams, epock);

			return true;
		}


	}



