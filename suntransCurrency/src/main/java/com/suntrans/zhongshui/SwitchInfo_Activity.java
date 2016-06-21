package com.suntrans.zhongshui;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import services.MainService;
import views.RiseNumberTextView;
import views.TouchListener;

import convert.Converts;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

public class SwitchInfo_Activity extends Activity implements SwipeRefreshLayout.OnRefreshListener{
    private String ipaddr;
    private Socket client;
    //private TextView tx1,tx2;
    private SwipeRefreshLayout swipe_container;    //下拉刷新控件
    private EditText et_maxi,et_uv,et_ov,et_urate,et_irate;   //配置参数的显示
    private ImageView img1,img2,img3,img4;
    private ImageView img_back;    //返回按钮
    private Button bt;   //更改配置按钮
    private int UValue,IValue,UV,OV,URate,IRate,MaxI;
    private RiseNumberTextView risetx_u,risetx_i;
    private String room;    //房间，是外间还是里间
    private String addr;  //第六感官地址
    public MainService.ibinder binder;  //用于Activity与Service通信
    private ServiceConnection con = new ServiceConnection() {
        @Override   //绑定服务成功后，调用此方法，获取返回的IBinder对象，可以用来调用Service中的方法
        public void onServiceConnected(ComponentName name, IBinder service) {
            //  Toast.makeText(getActivity().getApplication(), "绑定成功！", Toast.LENGTH_SHORT).show();
            binder=(MainService.ibinder)service;   //activity与service通讯的类，调用对象中的方法可以实现通讯
            //    Log.v("Time", "绑定后时间：" + String.valueOf(System.currentTimeMillis()));
        }

        @Override   //service因异常而断开的时候调用此方法
        public void onServiceDisconnected(ComponentName name) {
            Toast.makeText(getApplication(), "网络错误！", Toast.LENGTH_SHORT).show();

        }
    };;   ///用于绑定activity与service
    //新建广播接收器，接收服务器的数据并解析，根据第六感官的地址和开关的地址将数据转发到相应的Fragment
    private BroadcastReceiver broadcastreceiver=new BroadcastReceiver() {
        @Override
        public void onReceive (Context context, Intent intent){

            int count = intent.getIntExtra("ContentNum", 0);   //byte数组的长度
            byte[] data = intent.getByteArrayExtra("Content");  //内容数组
            String content = "";   //接收的字符串
            for (int i = 0; i < count; i++) {
                String s1 = Integer.toHexString((data[i] + 256) % 256);   //byte转换成十六进制字符串(先把byte转换成0-255之间的非负数，因为java中的数据都是带符号的)
                if (s1.length() == 1)
                    s1 = "0" + s1;
                content = content + s1;
            }
        }
    };//广播接收器
    private Handler handler1=new Handler()   //接收到TCP发回的数据后调用，用于更新列表中的开关状态
    {
        public void handleMessage(Message msg)
        {
            super.handleMessage(msg);
            if(msg.what==-1)
            {
                bt.setClickable(true);
                Toast.makeText(getApplicationContext(), "连接失败！", Toast.LENGTH_SHORT).show();
            }
            else
            {
                Map<String,Object> map=(Map<String,Object>)msg.obj;
                byte[] a=(byte[])(map.get("data"));    //byte数组a即为客户端发回的数据，aa68 0006是单个开通道，aa68 0003是所有的通道
                String s="";		               //保存命令的十六进制字符串
                for(int i=0;i<msg.what;i++)  //将数组转化成字符串
                {
                    String s1=Integer.toHexString((a[i] +256)%256);   //转换成十六进制字符串
                    if(s1.length()==1)
                        s1="0"+s1;
                    s=s+s1;
                }
                String crc=Converts.GetCRC(a, 2, msg.what-2-2);    //获取返回数据的校验码
                s=s.replace(" ", ""); //去掉空格
                int IsEffective=0;    //指令是否有效，0表示无效，1表示有效；对于和第六感官通讯而言，包头为ab68的数据才有效
                if(msg.what>5)
                    if(s.substring(0, 5).equals("ab680"))
                        IsEffective=1;

                if(s.length()==30&&IsEffective==1)     //如果返回的信息内容是电压值和电流值
                {
                    new Thread(){      //新建线程发送读取过压、欠压等值的命令，为了避免数据混乱，此命令在收到电压电流值反馈后发送
                        public void run(){
                            try
                            {
                                DataOutputStream out=new DataOutputStream(client.getOutputStream());
                                // 把用户输入的内容发送给server
                                String toServer = "ab68 0003 0201 0007";    //查询参数
                                toServer.replace(" ","");    //去掉空格
                                byte[] bt=null;
                                bt=Converts.HexString2Bytes(toServer);
                                String str=toServer+Converts.GetCRC(bt, 2, bt.length)+"0d0a";   //添加校验码
                                Log.i("Order", "发送的数据"+str);
                                byte[] bt1=Converts.HexString2Bytes(str);      //将完整的命令转换成十六进制
                                if(!client.isClosed())
                                {
                                    out.write(bt1);
                                    out.flush();
                                    //out.close();   //关闭输出流
                                }
                            }
                            catch (Exception e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }


                        }
                    }.start();
                    //根据反馈数据更新页面
                    UValue=(int) (((a[7])*256)/10.0+(a[8]&0x00ff)/10.0);
                    IValue=(int) ((a[9]*256+a[10]&0x00ff)/10.0);
                    int degree1=(int)(UValue*197/300);     //计算电压表指针旋转角度
                    int degree2=(int)(IValue*197/10);       //计算电流表指针旋转角度
                    //下面是动画
                    //照明电压仪表控制     创建一个AnimationSet对象，用于实现指针的旋转效果
                    AnimationSet animationSet1=new AnimationSet(true);
                    //创建一个RotateAnimation对象
                    RotateAnimation rotateAnimation1=new RotateAnimation(0,(degree1>197?197:degree1),Animation.RELATIVE_TO_SELF,0.5f,Animation.RELATIVE_TO_SELF,0.5f);
                    animationSet1.setFillAfter(true);// True:图片停在动画结束位置
                    //设置执行时间   ,单位是ms
                    rotateAnimation1.setDuration(1500);
                    //将ratateAnimation添加到animationSet中
                    animationSet1.addAnimation(rotateAnimation1);
                    //设置图片控件开始执行此动画Set
                    img2.startAnimation(animationSet1);

                    //照明电流仪表控制
                    AnimationSet animationSet2=new AnimationSet(true);
                    //创建一个RotateAnimation对象
                    RotateAnimation rotateAnimation2=new RotateAnimation(0,(degree2>197?197:degree2),Animation.RELATIVE_TO_SELF,0.5f,Animation.RELATIVE_TO_SELF,0.5f);
                    animationSet2.setFillAfter(true);// True:图片停在动画结束位置
                    //设置执行时间   ,单位是ms
                    rotateAnimation2.setDuration(1500);
                    //将ratateAnimation添加到animationSet中
                    animationSet2.addAnimation(rotateAnimation2);
                    //设置图片控件开始执行此动画Set
                    img4.startAnimation(animationSet2);
                    // tx1.setText("电压："+UValue+" V");
                    //tx2.setText("电流："+IValue+" A");
                    // 设置数据
                    risetx_u.withNumber(UValue);
                    // 设置动画播放时间
                    risetx_u.setDuration(1500);
                    // 开始播放动画
                    risetx_u.start();
                    // 设置数据
                    risetx_i.withNumber(IValue);
                    // 设置动画播放时间
                    risetx_i.setDuration(1500);
                    // 开始播放动画
                    risetx_i.start();
                }
                else if(s.length()==46&&IsEffective==1)    //如果返回的内容是配置信息内容
                {
                    MaxI=(int) (((a[5]&0x00ff)*256+a[6]&0x00ff));
                    UV=(int)(((a[7]&0x00ff)*256+a[8]&0x00ff));
                    OV=(int)(((a[9]&0x00ff)*256+a[10]&0x00ff));
                    URate=(int)(((a[15]&0x00ff)*256)+(a[16]&0x00ff));
                    IRate=(int)(((a[17]&0x00ff)*256)+(a[18]&0x00ff));
                    //  tx.setText("最大电流："+MaxI+"A\n"+"欠压："+UV+"V\n"+"过压："+OV+"V\n"+"电压系数："+URate+"\n电流系数："+IRate);
                    et_maxi.setText(MaxI+"");
                    et_uv.setText(UV+"");
                    et_ov.setText(OV+"");
                    et_urate.setText(URate+"");
                    et_irate.setText(IRate+"");
                }
                else if(s.length()==24&&IsEffective==1)    //返回的是配置成功的信息，则提示配置成功
                {
                    if(s.substring(6,8).equals("10"))
                    {
                        bt.setClickable(true);
                        Toast.makeText(getApplicationContext(),"配置成功！", Toast.LENGTH_SHORT).show();
                        new FreshThread().start();
                    }
                }

            }
        }


    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        Intent intent1=getIntent();
        room = intent1.getStringExtra("room");  //房间.
        //绑定MainService
        Intent intent = new Intent(getApplicationContext(), MainService.class);    //指定要绑定的service
        bindService(intent, con, Context.BIND_AUTO_CREATE);   //绑定主service
        // 注册自定义动态广播消息。根据Action识别广播
        IntentFilter filter_dynamic = new IntentFilter();
        filter_dynamic.addAction("com.suntrans.beijing.RECEIVE");  //为IntentFilter添加Action，接收的Action与发送的Action相同时才会出发onReceive
        registerReceiver(broadcastreceiver, filter_dynamic);    //动态注册broadcast receiver
        setContentView(R.layout.switchinfo);     //设置布局文件
        et_maxi=(EditText)findViewById(R.id.et_maxi);   //最大电流;
        et_uv=(EditText)findViewById(R.id.et_uv);    //欠压
        et_ov=(EditText)findViewById(R.id.et_ov);     //过压
        et_urate=(EditText)findViewById(R.id.et_urate);  //电压系数
        et_irate=(EditText)findViewById(R.id.et_irate);     //电流系数
        risetx_u=(RiseNumberTextView)findViewById(R.id.tx1);    //电压显示文字
        risetx_i=(RiseNumberTextView)findViewById(R.id.tx2);    //电流显示文字
        img1=(ImageView)findViewById(R.id.img1);    //电压表表盘
        img2=(ImageView)findViewById(R.id.img2);    //电压表指针
        img3=(ImageView)findViewById(R.id.img3);    //电流表表盘
        img4=(ImageView)findViewById(R.id.img4);    //电流表指针
        swipe_container=(SwipeRefreshLayout)findViewById(R.id.swipe_container);   //下拉控件
        swipe_container.setOnRefreshListener(this);
        swipe_container.setColorScheme(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);

        img_back=(ImageView)findViewById(R.id.img_back);
        img_back.setOnTouchListener(new TouchListener());
        img_back.setOnClickListener(new OnClickListener(){

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                finish();   //按下返回按钮
            }});
        bt=(Button)findViewById(R.id.bt);   //更改配置按钮
		/*Intent intent=getIntent();
		ipaddr=intent.getStringExtra("IPAddr");*/
        //在读取SharedPreferences数据前要实例化出一个SharedPreferences对象
        //SharedPreferences sharedPreferences= getSharedPreferences("data", Activity.MODE_PRIVATE);
        // 使用getString方法获得value，注意第2个参数是value的默认值
        //ipaddr =sharedPreferences.getString("clientip", "192.168.1.1");
        ipaddr="192.168.1.235";
        //Toast.makeText(getApplicationContext(), ipaddr, Toast.LENGTH_SHORT).show();
        bt.setOnTouchListener(new TouchListener());
        bt.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View v) {     //更改配置按钮
                bt.setClickable(false);
                // TODO Auto-generated method stub
                new Thread(){
                    public void run(){
                        try
                        {
                            DataOutputStream out=new DataOutputStream(client.getOutputStream());
                            //String toServer = "aa68 0010 0201 0007 0e 0060 0000 00fa 0001 0001 0199 0199";    //修改
                            //  toServer.replace(" ","");    //去掉空格           9    11   13   15   17   19   21
                            byte[] data=new byte[14];
                            data[0]=(byte)(Integer.valueOf(et_maxi.getText().toString())/256);
                            data[1]=(byte)((Integer.valueOf(et_maxi.getText().toString()))%256);   //最大电流
                            data[2]=(byte)(Integer.valueOf(et_uv.getText().toString())/256);
                            data[3]=(byte)((Integer.valueOf(et_uv.getText().toString()))%256);   //欠压
                            data[4]=(byte)(Integer.valueOf(et_ov.getText().toString())/256);
                            data[5]=(byte)((Integer.valueOf(et_ov.getText().toString()))%256);   //过压
                            data[6]=0x00;
                            data[7]=0x00;
                            data[8]=0x00;
                            data[9]=0x00;
                            data[10]=(byte)(Integer.valueOf(et_urate.getText().toString())/256);    //电压系数高八位
                            data[11]=(byte)((Integer.valueOf(et_urate.getText().toString()))%256);   //电压系数低八位
                            data[12]=(byte)(Integer.valueOf(et_irate.getText().toString())/256);   //电流系数高八位
                            data[13]=(byte)((Integer.valueOf(et_irate.getText().toString()))%256);   //电流系数低八位
                            byte[] bt=null; //+
                            String toServer="ab68 0010 0201 0007 0e"+Converts.Bytes2HexString(data);
                            toServer.replace(" ","");    //去掉空格
                            bt=Converts.HexString2Bytes(toServer);
                            String str=toServer+Converts.GetCRC(bt, 2, bt.length)+"0d0a";   //添加校验码
                            byte[] bt1=Converts.HexString2Bytes(str);      //将完整的命令转换成十六进制
                            //Log.i("Order", Converts.Bytes2HexString(bt1));   //打印命令内容
                            if(client!=null)    //如果数组中存在此socket，则检测是否关闭
                            {
                                if(!client.isOutputShutdown()&&!client.isClosed()&&client.isConnected())     //如果输出通道没有关，且正在连接中
                                {
                                    out.write(bt1);
                                    out.flush();
                                    //out.close();   //关闭输出流
                                }
                            }

                        }
                        catch(Exception e){
                            Message msg=new Message();
                            msg.what=-1;   //数组有效数据长度
                            handler1.sendMessage(msg);
                        }}
                }.start();
            }});
    }
    @Override
    public void onRefresh()
    {
        //Toast.makeText(getActivity().getApplicationContext(), "刷新动作！",Toast.LENGTH_SHORT).show();
        new Handler().postDelayed(new Runnable() {
            @Override public void run() {
                new FreshThread().start();   //开始刷新线程
                swipe_container.setRefreshing(false);
            }
        }, 1000);
    }
    private void TCPInit() throws UnknownHostException{
        //获取服务器ip
        final InetAddress serverAddr = InetAddress.getByName(ipaddr);// TCPServer.SERVERIP
        //获取服务器端口
        final int port=2000;
        //定义socketaddress
        final SocketAddress my_sockaddr = new InetSocketAddress(serverAddr, port);
        new Thread(){      //不能在主线程中访问网络，所以要新建线程
            public void run(){
                try
                {
                    client = new Socket(serverAddr, port);   //新建TCP连接
                    //client.connect(my_sockaddr,5000);	  //第二个参数是timeout
                    DataOutputStream out=new DataOutputStream(client.getOutputStream());
                    // 把用户输入的内容发送给server
                    String toServer = "ab68 0003 0100 0003";    //查询参数
                    toServer.replace(" ","");    //去掉空格
                    byte[] bt=null;
                    bt=Converts.HexString2Bytes(toServer);
                    String str=toServer+Converts.GetCRC(bt, 2, bt.length)+"0d0a";   //添加校验码
                    Log.i("Order", "发送的数据"+str);
                    byte[] bt1=Converts.HexString2Bytes(str);      //将完整的命令转换成十六进制
                    if(!client.isClosed())
                    {
                        out.write(bt1);
                        out.flush();
                        //out.close();   //关闭输出流
                    }
                    new TCPServerThread().start();    //开启新的线程接收数据
		           /* try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}*/
		          /* toServer = "ab68 0003 0201 0007";    //查询参数
		           toServer.replace(" ","");    //去掉空格			    		            
		           bt=null;
		           bt=Converts.HexString2Bytes(toServer);
		           str=toServer+Converts.GetCRC(bt, 2, bt.length)+"0d0a";   //添加校验码
		           Log.i("Order", "发送的数据"+str);
		           bt1=Converts.HexString2Bytes(str);      //将完整的命令转换成十六进制
		            if(!client.isClosed())
		            {
		                out.write(bt1);  
			            out.flush();
			            //out.close();   //关闭输出流
		            }	
	                */
                }
                catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }


            }
        }.start();

    }

    public class TCPServerThread extends Thread
    {
        // String ipaddr="";
        public void run()
        {
            //tvRecv.setText("start");
            byte[] buffer = new byte[1024];
            final StringBuilder sb = new StringBuilder();
            try {
                // 接收服务器信息       定义输入流
                InputStream in=client.getInputStream();
                DataInputStream ins = new DataInputStream(in);
                while (client!=null) {
                    //content=new byte[1024];
                    if (!client.isClosed()) {
                        if (client.isConnected()) {
                            if (!client.isInputShutdown()) {
                                byte[] content=new byte[50];
                                int count=0;   //记录接收数据数组的长度
                                while((count=ins.read(content)) !=-1) {     //读取数据 ，存放到缓存区content中

                                    Map<String,Object> map=new HashMap<String,Object>();   //新建map存放要传递给主线程的数据
                                    map.put("data",content);    //客户端发回的数据
                                    Message msg=new Message();
                                    msg.what=count;   //数组有效数据长度
                                    msg.obj=map;  //接收到的数据数组
                                    handler1.sendMessage(msg);

                                }
                            }
                        }
                    }
                }
                Log.i("IBM","parameter--tcp退出1");
            } catch (Exception e) {
                e.printStackTrace();
                Log.i("IBM","parameter--tcp退出2");
            }
        }
    }
    //自定义刷新线程内部类，用于向开关请求电压，电流，和配置参数数据
    public class FreshThread extends Thread
    {
        public void run()
        {
            try{
                DataOutputStream out=new DataOutputStream(client.getOutputStream());
                // 把用户输入的内容发送给server
                String toServer = "ab68 0003 0100 0003";    //查询参数
                toServer.replace(" ","");    //去掉空格
                byte[] bt=null;
                bt=Converts.HexString2Bytes(toServer);
                String str=toServer+Converts.GetCRC(bt, 2, bt.length)+"0d0a";   //添加校验码
                Log.i("Order", "发送的数据"+str);
                byte[] bt1=Converts.HexString2Bytes(str);      //将完整的命令转换成十六进制
                if(!client.isClosed()&&!client.isOutputShutdown())
                {
                    out.write(bt1);
                    out.flush();
                    //out.close();   //关闭输出流
                }
	         /*try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}*/
	        /*toServer = "ab68 0003 0201 0007";    //查询参数
	        toServer.replace(" ","");    //去掉空格			    		            
	        bt=null;
	        bt=Converts.HexString2Bytes(toServer);
	         str=toServer+Converts.GetCRC(bt, 2, bt.length)+"0d0a";   //添加校验码
	         Log.i("Order", "发送的数据"+str);
	        bt1=Converts.HexString2Bytes(str);      //将完整的命令转换成十六进制
	         if(!client.isClosed()&&!client.isOutputShutdown())
	         {
	             out.write(bt1);  
		         out.flush();
		            //out.close();   //关闭输出流
	         }	*/

            }
            catch (Exception e) {
                // TODO Auto-generated catch block
                Message msg=new Message();
                msg.what=-1;   //-1代表出错，刷新失败
                handler1.sendMessage(msg);
            }
        }
    }
    @Override      //关闭时调用
    protected void onDestroy()
    {
        Log.i("Order","switchinfo关闭");
        unbindService(con);   //解除Service的绑定
        unregisterReceiver(broadcastreceiver);  //注销广播接收者
        super.onDestroy();
    }
	/*
	@Override           //menu选项监听
	public boolean onMenuItemSelected(int featureId, MenuItem item)
	{
		if(item.getItemId() == android.R.id.home)    //如果按下的是返回键
	    {
	        finish();
	        return true;
	    }
		else if(item.getItemId()==R.id.action_refresh)   //如果按下的是标题栏的刷新键
		{
			new FreshThread().start();
            return true;
			
		}     
		else
			return true;
	}*/
}