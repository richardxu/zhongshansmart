package com.suntrans.zhongshui;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import services.MainService;
import views.TouchListener;

import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.handmark.pulltorefresh.library.PullToRefreshBase.Mode;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;

import convert.Converts;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class ControlInfo_Activity extends Activity {
    private PullToRefreshListView mPullRefreshListView;    //下拉列表控件
    private ListView list;   //列表
    private String clientip;  //服务器IP
    private Socket client;    //保持TCP连接的socket
    private ImageView img_back;   //返回按钮
    private Button bt;   //更改配置按钮
    private long time;
    private String which;
    private String room;   //房间。外间或里间
    private String addr;   //根据房间确定第六感官的地址
    private ProgressDialog progressdialog;
    private ArrayList<Map<String, String>> data=new ArrayList<Map<String, String>>();    //室内环境
    public MainService.ibinder binder;  //用于Activity与Service通信
    private ServiceConnection con = new ServiceConnection() {
        @Override   //绑定服务成功后，调用此方法，获取返回的IBinder对象，可以用来调用Service中的方法
        public void onServiceConnected(ComponentName name, IBinder service) {
            Toast.makeText(getApplication(), "绑定成功！", Toast.LENGTH_SHORT).show();
            binder=(MainService.ibinder)service;   //activity与service通讯的类，调用对象中的方法可以实现通讯
            binder.sendOrder(addr+"f003 000e",4);
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
            Map<String, Object> map = new HashMap<String, Object>();   //新建map存放要传递给主线程的数据
            map.put("data", data);    //客户端发回的数据
            Message msg = new Message();   //新建Message，用于向handler传递数据
            msg.what = count;   //数组有效数据长度
            msg.obj = map;  //接收到的数据数组
            if (count > 5 && content.substring(4, 10).equals(addr+"f0"))   //通过Fragment的handler将数据传过去
            {
                handler1.sendMessage(msg);
            }


        }
    };//广播接收器
    public Handler handler1=new Handler()   //接收到TCP发回的数据后调用，用于更新列表中的开关状态，即反馈
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
              //  String ipaddr=(String)(map.get("ipaddr"));    //开关的IP地址
                String s="";		               //保存命令的十六进制字符串
                for(int i=0;i<msg.what;i++)
                {
                    String s1=Integer.toHexString((a[ i ] +256)%256);   //byte转换成十六进制字符串(先把byte转换成0-255之间的非负数，因为java中的数据都是带符号的)
                    if(s1.length()==1)
                        s1="0"+s1;
                    s=s+s1;
                }
                String crc=Converts.GetCRC(a, 4, msg.what-2-2);    //获取返回数据的校验码，倒数第3、4位是验证码
                s=s.replace(" ", ""); //去掉空格
                // Log.i("Order", "收到数据："+s);
                int IsEffective=0;    //指令是否有效，0表示无效，1表示有效；对于和第六感官通讯而言，包头为ab68的数据才有效
                if(msg.what>5)
                 //   if(s.substring(0, 5).equals("ab68f"))
                        IsEffective=1;
                if(IsEffective==1)   //如果数据有效，则进行解析，并更新页面
                {
                    if(s.substring(10,12).equals("03")||s.substring(10,12).equals("04"))   //如果是读寄存器状态，则判断是读寄存器1（室内参数），还是寄存器2（灯光信息）的状态
                    {
                        if(s.substring(12,14).equals("22"))  //寄存器1，长度34个字节，所有的参数值
                        {
                            //计算得到各个参数的值，顺序是按寄存器顺序来的
                            double tmp1=(((a[7]+256)%256)*256+(a[8]+256)%256)/100.0;   //温度1
                            double tmp2=(((a[9]+256)%256)*256+(a[10]+256)%256)/10.0;   //温度2
                            double tmp3=(((a[11]+256)%256)*256+(a[12]+256)%256)/10.0;   //温度3
                            double humidity=(((a[13]+256)%256)*256+(a[14]+256)%256)+20;   //湿度
                            double atm=(((a[15]+256)%256)*256+(a[16]+256)%256)/100.0;       //大气压
                            double arofene=(((a[17]+256)%256)*256+(a[18]+256)%256)/1000.0;    //甲醛
                            double smoke=(((a[19]+256)%256)*256+(a[20]+256)%256);       //烟雾
                            data.get(0).put("Value", String.valueOf(tmp1)+" ℃");
                            data.get(1).put("Value", String.valueOf(tmp2)+" ℃");
                            data.get(2).put("Value", String.valueOf(tmp3)+" ℃");
                            data.get(3).put("Value", String.valueOf(humidity)+" %RH");
                            data.get(4).put("Value", String.valueOf(atm)+" kPa");
                            data.get(5).put("Value", String.valueOf(arofene)+" ppm");
                            data.get(6).put("Value", String.valueOf(smoke)+" ppm");
                            // Adapter adapter=(Adapter)list.getAdapter();
                            //adapter.notifyDataSetChanged();
                            list.setAdapter(new Adapter());
                        }
                        else if(s.substring(12, 14).equals("1c"))  //寄存器6，长度28个字节，存放参数的系数K,B，每个参数都有两个字节，高8位是符号位，02是+的，00是-的
                        {
                            int tmp1_k=(a[7]==2?1:-1)*(a[8]&0xff);
                            int tmp1_b=(a[9]==2?1:-1)*(a[10]&0xff);

                            int tmp2_k=(a[11]==2?1:-1)*(a[12]&0xff);
                            int tmp2_b=(a[13]==2?1:-1)*(a[14]&0xff);

                            int tmp3_k=(a[15]==2?1:-1)*(a[16]&0xff);
                            int tmp3_b=(a[17]==2?1:-1)*(a[18]&0xff);

                            int humidity_k=(a[19]==2?1:-1)*(a[20]&0xff);
                            int humidity_b=(a[21]==2?1:-1)*(a[22]&0xff);

                            int atm_k=(a[23]==2?1:-1)*(a[24]&0xff);
                            int atm_b=(a[25]==2?1:-1)*(a[26]&0xff);

                            int arofene_k=(a[27]==2?1:-1)*(a[28]&0xff);
                            int arofene_b=(a[29]==2?1:-1)*(a[30]&0xff);

                            int smoke_k=(a[31]==2?1:-1)*(a[32]&0xff);
                            int smoke_b=(a[33]==2?1:-1)*(a[34]&0xff);

                            data.get(0).put("KValue", String.valueOf(tmp1_k));
                            data.get(0).put("BValue", String.valueOf(tmp1_b));
                            data.get(1).put("KValue", String.valueOf(tmp2_k));
                            data.get(1).put("BValue", String.valueOf(tmp2_b));
                            data.get(2).put("KValue", String.valueOf(tmp3_k));
                            data.get(2).put("BValue", String.valueOf(tmp3_b));
                            data.get(3).put("KValue", String.valueOf(humidity_k));
                            data.get(3).put("BValue", String.valueOf(humidity_b));
                            data.get(4).put("KValue", String.valueOf(atm_k));
                            data.get(4).put("BValue", String.valueOf(atm_b));
                            data.get(5).put("KValue", String.valueOf(arofene_k));
                            data.get(5).put("BValue", String.valueOf(arofene_b));
                            data.get(6).put("KValue", String.valueOf(smoke_k));
                            data.get(6).put("BValue", String.valueOf(smoke_b));

                            list.setAdapter(new Adapter());
                        }
                    }
                    else if(s.substring(10,12).equals("10"))  //如果收到的是写多个寄存器
                    {
                        bt.setClickable(true);
                        Toast.makeText(getApplicationContext(),"配置成功！", Toast.LENGTH_SHORT).show();
                       // new FreshThread().start();
                    }
                }
            }
        }
    };

    private Handler handler2=new Handler()   //用来控制progressdialog的显示和销毁
    {
        public void handleMessage(Message msg)
        {
            super.handleMessage(msg);
            if(msg.what==0)   //如果是要关闭progresedialog的显示（收到相应通道的反馈，则进行此操作）
            {
                if(progressdialog!= null)
                {
                    progressdialog.dismiss();
                    progressdialog=null;
                }
                //which="100";
            }
            else if(msg.what==1)   //是要显示progressdialog
            {
                progressdialog = new ProgressDialog(ControlInfo_Activity.this);    //初始化progressdialog
                progressdialog.setCancelable(true);// 设置是否可以通过点击Back键取消
                progressdialog.setCanceledOnTouchOutside(false);// 设置在点击Dialog外是否取消Dialog进度条
                progressdialog.show();
                progressdialog.setContentView(R.layout.progressdialog);    //设置显示的内容
            }
            else if(msg.what==2)   //如果是要根据时间判断是否关闭progressdialog的显示，用于通讯条件不好，收不到反馈时
            {
                if(new Date().getTime()-time>=1900)
                {
                    if(progressdialog!= null)
                    {
                        progressdialog.dismiss();
                        progressdialog=null;
                    }
                    if(!which.equals("100"))
                    {
                        which="100";
                        Toast.makeText(ControlInfo_Activity.this, "网络错误！", Toast.LENGTH_SHORT).show();
                    }
                }

            }
            else   //如果是要显示progressdialog
            {
                progressdialog = new ProgressDialog(ControlInfo_Activity.this);    //初始化progressdialog
                progressdialog.setCancelable(true);// 设置是否可以通过点击Back键取消
                progressdialog.setCanceledOnTouchOutside(false);// 设置在点击Dialog外是否取消Dialog进度条
                progressdialog.show();
                progressdialog.setContentView(R.layout.progressdialog);    //设置显示的内容
            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        Intent intent1=getIntent();
        room = intent1.getStringExtra("room");  //房间.
        if(room.equals("里间"))
            addr="0003";
        else
            addr="0002";
        //绑定MainService
        Intent intent = new Intent(getApplicationContext(), MainService.class);    //指定要绑定的service
        bindService(intent, con, Context.BIND_AUTO_CREATE);   //绑定主service
        // 注册自定义动态广播消息。根据Action识别广播
        IntentFilter filter_dynamic = new IntentFilter();
        filter_dynamic.addAction("com.suntrans.beijing.RECEIVE");  //为IntentFilter添加Action，接收的Action与发送的Action相同时才会出发onReceive
        registerReceiver(broadcastreceiver, filter_dynamic);    //动态注册broadcast receiver
        setContentView(R.layout.controlinfo);     //设置布局文件

        DbInit();
        mPullRefreshListView = (PullToRefreshListView)findViewById(R.id.list);   //下拉列表控件
        list=mPullRefreshListView.getRefreshableView();   //从下拉列表控件中获取
        mPullRefreshListView.setMode(Mode.PULL_FROM_START);//只有下拉刷新
        list.setAdapter(new Adapter());   //设置适配器
        // 列表下拉监听
        mPullRefreshListView.setOnRefreshListener(new OnRefreshListener<ListView>() {
            @Override
            public void onRefresh(PullToRefreshBase<ListView> refreshView)
            {
                String label = DateUtils.formatDateTime(getApplicationContext(), System.currentTimeMillis(),
                        DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_ALL);

                // Update the LastUpdatedLabel
                refreshView.getLoadingLayoutProxy().setLastUpdatedLabel("上次刷新："+label);
                // Do work to refresh the list here.
                new GetDataTask().execute();   //执行任务
            }
        });
        img_back=(ImageView)findViewById(R.id.img_back);
        img_back.setOnTouchListener(new TouchListener());
        img_back.setOnClickListener(new OnClickListener(){

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                finish();
            }});
       // bt=(Button)findViewById(R.id.bt);
//        bt.setOnTouchListener(new TouchListener());
//        bt.setOnClickListener(new OnClickListener(){
//            @Override
//            public void onClick(View v) {
//                bt.setClickable(false);
//                // TODO Auto-generated method stub
//                new Thread(){
//                    public void run(){
//                        try
//                        {
//                            DataOutputStream out=new DataOutputStream(client.getOutputStream());
//                            //String toServer = "aa68 0010 0201 0007 0e 0060 0000 00fa 0001 0001 0199 0199";    //修改
//                            //  toServer.replace(" ","");    //去掉空格           9    11   13   15   17   19   21
//                            int tmp1_k=Integer.valueOf(data.get(0).get("KValue"));
//                            int tmp1_b=Integer.valueOf(data.get(0).get("BValue"));
//                            int tmp2_k=Integer.valueOf(data.get(1).get("KValue"));
//                            int tmp2_b=Integer.valueOf(data.get(1).get("BValue"));
//                            int tmp3_k=Integer.valueOf(data.get(2).get("KValue"));
//                            int tmp3_b=Integer.valueOf(data.get(2).get("BValue"));
//                            int humidity_k=Integer.valueOf(data.get(3).get("KValue"));
//                            int humidity_b=Integer.valueOf(data.get(3).get("BValue"));
//                            int atm_k=Integer.valueOf(data.get(4).get("KValue"));
//                            int atm_b=Integer.valueOf(data.get(4).get("BValue"));
//                            int arofene_k=Integer.valueOf(data.get(5).get("KValue"));
//                            int arofene_b=Integer.valueOf(data.get(5).get("BValue"));
//                            int smoke_k=Integer.valueOf(data.get(6).get("KValue"));
//                            int smoke_b=Integer.valueOf(data.get(6).get("BValue"));
//                            byte[] a=new byte[28];
//                            a[0]=(byte)(tmp1_k>0?2:0);
//                            a[1]=(byte)(Math.abs(tmp1_k)%256);    //温度1，系数k值
//
//                            a[2]=(byte)(tmp1_b>0?2:0);
//                            a[3]=(byte)(Math.abs(tmp1_b)%256);    //温度1，系数b值
//
//                            a[4]=(byte)(tmp2_k>0?2:0);
//                            a[5]=(byte)(Math.abs(tmp2_k)%256);    //温度2，系数k值
//
//                            a[6]=(byte)(tmp2_b>0?2:0);
//                            a[7]=(byte)(Math.abs(tmp2_b)%256);    //温度2，系数b值
//
//                            a[8]=(byte)(tmp3_k>0?2:0);
//                            a[9]=(byte)(Math.abs(tmp3_k)%256);    //温度3，系数k值
//
//                            a[10]=(byte)(tmp3_b>0?2:0);
//                            a[11]=(byte)(Math.abs(tmp3_b)%256);    //温度3，系数b值
//
//                            a[12]=(byte)(humidity_k>0?2:0);
//                            a[13]=(byte)(Math.abs(humidity_k)%256);    //湿度，系数k值
//
//                            a[14]=(byte)(humidity_b>0?2:0);
//                            a[15]=(byte)(Math.abs(humidity_b)%256);    //湿度，系数b值
//
//                            a[16]=(byte)(atm_k>0?2:0);
//                            a[17]=(byte)(Math.abs(atm_k)%256);    //大气压，系数k值
//
//                            a[18]=(byte)(atm_b>0?2:0);
//                            a[19]=(byte)(Math.abs(atm_b)%256);    //大气压，系数b值
//
//                            a[20]=(byte)(arofene_k>0?2:0);
//                            a[21]=(byte)(Math.abs(arofene_k)%256);    //甲醛，系数k值
//
//                            a[22]=(byte)(arofene_b>0?2:0);
//                            a[23]=(byte)(Math.abs(arofene_b)%256);    //甲醛，系数b值
//
//                            a[24]=(byte)(smoke_k>0?2:0);
//                            a[25]=(byte)(Math.abs(smoke_k)%256);    //烟雾，系数k值
//
//                            a[26]=(byte)(smoke_b>0?2:0);
//                            a[27]=(byte)(Math.abs(smoke_b)%256);    //烟雾，系数b值
//
//
//                            byte[] bt=null; //+
//                            String toServer="ab68 f010 0600 000e 1c"+Converts.Bytes2HexString(a);
//                            toServer.replace(" ","");    //去掉空格
//                            bt=Converts.HexString2Bytes(toServer);
//                            String str=toServer+Converts.GetCRC(bt, 2, bt.length)+"0d0a";   //添加校验码
//                            byte[] bt1=Converts.HexString2Bytes(str);      //将完整的命令转换成十六进制
//                            //Log.i("Order", Converts.Bytes2HexString(bt1));   //打印命令内容
//                            if(client!=null)    //如果数组中存在此socket，则检测是否关闭
//                            {
//                                if(!client.isOutputShutdown()&&!client.isClosed()&&client.isConnected())     //如果输出通道没有关，且正在连接中
//                                {
//                                    out.write(bt1);
//                                    out.flush();
//                                    //out.close();   //关闭输出流
//                                }
//                            }
//
//                        }
//                        catch(Exception e){
//                            Message msg=new Message();
//                            msg.what=-1;   //数组有效数据长度
//                            handler1.sendMessage(msg);
//                        }}
//                }.start();
//            }});

    }
    @Override      //关闭时调用,将所有的socket连接关闭
    protected void onDestroy()
    {

        Log.i("Order","controlinfo关闭");
        unbindService(con);   //解除Service的绑定
        unregisterReceiver(broadcastreceiver);  //注销广播接收者
        super.onDestroy();
    }


    public class TCPServerThread extends Thread
    {
        String ipaddr="";
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
                                    map.put("ipaddr",ipaddr);   //客户端的IP地址
                                    Message msg=new Message();
                                    msg.what=count;   //数组有效数据长度
                                    msg.obj=map;  //接收到的数据数组
                                    handler1.sendMessage(msg);

                                }
                            }
                        }
                    }
                }
                // Log.i("Info", "TCP接收监听退出");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    private void DbInit()
    {
        Map<String,String> map1=new HashMap<String,String>();
        map1.put("Name", "温度1");     //参数名称      kx+b=显示值，x为测量值
        map1.put("KValue", "10");         //系数k值
        map1.put("BValue", "0");    //系数b值
        map1.put("Value", "null ℃");    //温度值
        data.add(map1);

        Map<String,String> map2=new HashMap<String,String>();
        map2.put("Name", "温度2");     //参数名称      kx+b=显示值，x为测量值
        map2.put("KValue", "10");         //系数k值
        map2.put("BValue", "0");    //系数b值
        map2.put("Value", "null ℃");    //温度值
        data.add(map2);

        Map<String,String> map3=new HashMap<String,String>();
        map3.put("Name", "温度3");     //参数名称      kx+b=显示值，x为测量值
        map3.put("KValue", "10");         //系数k值
        map3.put("BValue", "0");    //系数b值
        map3.put("Value", "null ℃");    //温度值
        data.add(map3);

        Map<String,String> map4=new HashMap<String,String>();
        map4.put("Name", "湿度");     //参数名称      kx+b=显示值，x为测量值
        map4.put("KValue", "10");         //系数k值
        map4.put("BValue", "0");    //系数b值
        map4.put("Value", "null %RH");
        data.add(map4);

        Map<String,String> map5=new HashMap<String,String>();
        map5.put("Name", "大气压");     //参数名称      kx+b=显示值，x为测量值
        map5.put("KValue", "10");         //系数k值
        map5.put("BValue", "0");    //系数b值
        map5.put("Value", "null kPa");
        data.add(map5);

        Map<String,String> map6=new HashMap<String,String>();
        map6.put("Name", "甲醛");     //参数名称      kx+b=显示值，x为测量值
        map6.put("KValue", "10");         //系数k值
        map6.put("BValue", "0");    //系数b值
        map6.put("Value", "null ppm");
        data.add(map6);

        Map<String,String> map7=new HashMap<String,String>();
        map7.put("Name", "烟雾");     //参数名称      kx+b=显示值，x为测量值
        map7.put("KValue", "10");         //系数k值
        map7.put("BValue", "0");    //系数b值
        map7.put("Value", "null ppm");
        data.add(map7);

        Map<String,String> map8=new HashMap<String,String>();
        map8.put("Name", "null");     //参数名称      kx+b=显示值，x为测量值
        map8.put("KValue", "null");         //系数k值
        map8.put("BValue", "null");    //系数b值
        map8.put("Value", "null");
        data.add(map8);
    }

    ///下拉刷新处理的函数。
    private class GetDataTask extends AsyncTask<Void, Void, String>
    {
        // 后台处理部分
        @Override
        protected String doInBackground(Void... params)
        {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            SendOrder("F003 0100 0007",false);
	    		/*try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	    		which="2";   //2表示查询所有参数状态
	    		SendOrder("f003 0200 0006",true);*/
            return "1";
        }

        //这里是对刷新的响应，可以利用addFirst（）和addLast()函数将新加的内容加到LISTView中
        //根据AsyncTask的原理，onPostExecute里的result的值就是doInBackground()的返回值
        @Override
        protected void onPostExecute(String result) {
            if(result.equals("1"))  //请求数据成功，根据显示的页面重新初始化listview
            {

            }
            else            //请求数据失败
            {
                Toast.makeText(ControlInfo_Activity.this.getApplicationContext(), "加载失败！", Toast.LENGTH_SHORT).show();
            }
            // Call onRefreshComplete when the list has been refreshed.
            mPullRefreshListView.onRefreshComplete();   //表示刷新完成

            super.onPostExecute(result);//这句是必有的，AsyncTask规定的格式
        }
    }
    //自定义Adapter
    class Adapter extends BaseAdapter{
        @Override
        public int getCount() {       //列表的条目
            return data.size();
        }
        @Override
        public Object getItem(int position) {
            return data.get(position);
        }
        @Override
        public long getItemId(int position) {
            return position;
        }
        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            convertView = LayoutInflater.from(getApplication()).inflate(R.layout.controlinfo_listview, null);
            EditText value_k=(EditText)convertView.findViewById(R.id.value_k);   //k值
            EditText value_b=(EditText)convertView.findViewById(R.id.value_b);   //b值
            TextView name=(TextView)convertView.findViewById(R.id.name);    //名称
            TextView value=(TextView)convertView.findViewById(R.id.value);   //值

            LinearLayout layout1 = (LinearLayout) convertView.findViewById(R.id.layout1);
            if(position<7) {
                Map<String, String> map = data.get(position);
                name.setText(map.get("Name"));
                value.setText(map.get("Value"));
                value_k.setText(map.get("KValue"));
                value_b.setText(map.get("BValue"));
                layout1.setVisibility(View.VISIBLE);
                Button bt1=(Button)convertView.findViewById(R.id.bt);   //更改设置按钮
                bt1.setVisibility(View.GONE);
            }
            else {
                bt=(Button)convertView.findViewById(R.id.bt);   //更改设置按钮
                layout1.setVisibility(View.GONE);
                bt.setVisibility(View.VISIBLE);
                bt.setOnTouchListener(new TouchListener());
                bt.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {

                    bt.setClickable(false);
                    // TODO Auto-generated method stub
                    new Thread(){
                        public void run(){
                            try
                            {
                                DataOutputStream out=new DataOutputStream(client.getOutputStream());
                                //String toServer = "aa68 0010 0201 0007 0e 0060 0000 00fa 0001 0001 0199 0199";    //修改
                                //  toServer.replace(" ","");    //去掉空格           9    11   13   15   17   19   21
                                int tmp1_k=Integer.valueOf(data.get(0).get("KValue"));
                                int tmp1_b=Integer.valueOf(data.get(0).get("BValue"));
                                int tmp2_k=Integer.valueOf(data.get(1).get("KValue"));
                                int tmp2_b=Integer.valueOf(data.get(1).get("BValue"));
                                int tmp3_k=Integer.valueOf(data.get(2).get("KValue"));
                                int tmp3_b=Integer.valueOf(data.get(2).get("BValue"));
                                int humidity_k=Integer.valueOf(data.get(3).get("KValue"));
                                int humidity_b=Integer.valueOf(data.get(3).get("BValue"));
                                int atm_k=Integer.valueOf(data.get(4).get("KValue"));
                                int atm_b=Integer.valueOf(data.get(4).get("BValue"));
                                int arofene_k=Integer.valueOf(data.get(5).get("KValue"));
                                int arofene_b=Integer.valueOf(data.get(5).get("BValue"));
                                int smoke_k=Integer.valueOf(data.get(6).get("KValue"));
                                int smoke_b=Integer.valueOf(data.get(6).get("BValue"));
                                byte[] a=new byte[28];
                                a[0]=(byte)(tmp1_k>0?2:0);
                                a[1]=(byte)(Math.abs(tmp1_k)%256);    //温度1，系数k值

                                a[2]=(byte)(tmp1_b>0?2:0);
                                a[3]=(byte)(Math.abs(tmp1_b)%256);    //温度1，系数b值

                                a[4]=(byte)(tmp2_k>0?2:0);
                                a[5]=(byte)(Math.abs(tmp2_k)%256);    //温度2，系数k值

                                a[6]=(byte)(tmp2_b>0?2:0);
                                a[7]=(byte)(Math.abs(tmp2_b)%256);    //温度2，系数b值

                                a[8]=(byte)(tmp3_k>0?2:0);
                                a[9]=(byte)(Math.abs(tmp3_k)%256);    //温度3，系数k值

                                a[10]=(byte)(tmp3_b>0?2:0);
                                a[11]=(byte)(Math.abs(tmp3_b)%256);    //温度3，系数b值

                                a[12]=(byte)(humidity_k>0?2:0);
                                a[13]=(byte)(Math.abs(humidity_k)%256);    //湿度，系数k值

                                a[14]=(byte)(humidity_b>0?2:0);
                                a[15]=(byte)(Math.abs(humidity_b)%256);    //湿度，系数b值

                                a[16]=(byte)(atm_k>0?2:0);
                                a[17]=(byte)(Math.abs(atm_k)%256);    //大气压，系数k值

                                a[18]=(byte)(atm_b>0?2:0);
                                a[19]=(byte)(Math.abs(atm_b)%256);    //大气压，系数b值

                                a[20]=(byte)(arofene_k>0?2:0);
                                a[21]=(byte)(Math.abs(arofene_k)%256);    //甲醛，系数k值

                                a[22]=(byte)(arofene_b>0?2:0);
                                a[23]=(byte)(Math.abs(arofene_b)%256);    //甲醛，系数b值

                                a[24]=(byte)(smoke_k>0?2:0);
                                a[25]=(byte)(Math.abs(smoke_k)%256);    //烟雾，系数k值

                                a[26]=(byte)(smoke_b>0?2:0);
                                a[27]=(byte)(Math.abs(smoke_b)%256);    //烟雾，系数b值


                                byte[] bt=null; //+
                                String toServer="ab68 f010 0600 000e 1c"+Converts.Bytes2HexString(a);
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
                }}

                );
            }
            //内容改变监听
            value_k.addTextChangedListener(new TextWatcher(){

                @Override
                public void beforeTextChanged(CharSequence s, int start,
                                              int count, int after) {
                    // TODO Auto-generated method stub

                }
                @Override
                public void onTextChanged(CharSequence s, int start,
                                          int before, int count) {
                    // TODO Auto-generated method stub
                    //int k=Integer.valueOf(s.toString());

                    data.get(position).put("KValue", s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {
                    // TODO Auto-generated method stub

                }});
            //内容改变监听
            value_b.addTextChangedListener(new TextWatcher(){

                @Override
                public void beforeTextChanged(CharSequence s, int start,
                                              int count, int after) {
                    // TODO Auto-generated method stub

                }
                @Override
                public void onTextChanged(CharSequence s, int start,
                                          int before, int count) {
                    // TODO Auto-generated method stub
                    data.get(position).put("BValue", s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {
                    // TODO Auto-generated method stub

                }});

            return convertView;
        }
    }
    //自定义刷新线程内部类，只请求参数值，即寄存器1，收到反馈后，在处理函数中发送读取寄存器6的命令
    public class FreshThread extends Thread
    {
        public void run()
        {
            try{
                DataOutputStream out=new DataOutputStream(client.getOutputStream());
                // 把用户输入的内容发送给server
                String toServer = "ab68 F003 0100 0007";  //读室内信息，长度为7个寄存器
                toServer.replace(" ","");    //去掉空格
                byte[] bt=null;
                bt=Converts.HexString2Bytes(toServer);
                String str=toServer+Converts.GetCRC(bt, 2, bt.length)+"0d0a";   //添加校验码
                // Log.i("Order", "发送的数据"+str);
                byte[] bt1=Converts.HexString2Bytes(str);      //将完整的命令转换成十六进制
                if(!client.isClosed()&&!client.isOutputShutdown())
                {
                    out.write(bt1);
                    out.flush();
                    //out.close();   //关闭输出流
                }
            }
            catch (Exception e) {
                // TODO Auto-generated catch block
                Message msg=new Message();
                msg.what=-1;   //-1代表出错，刷新失败
                handler1.sendMessage(msg);
            }
        }
    }

    /***
     * 向第六感官发送数据的函数，输入参数为要发送的字符串（未加校验、包头、包尾的），函数中会添加校验和包头包尾
     * @param order     要发送的原始命令
     * @param IsShow     是否要显示progressdialog，调节灯光亮度时不显示，刷新页面和打开、关闭灯光时显示
     */
    //
    private void SendOrder(final String order,boolean IsShow)
    {
        //	if(which.equals("100"))   //首先判断是否有开关命令正在执行，如果没有则向开关发送命令
        //	{
        //Log.i("Time","点击按钮+"+String.valueOf(System.currentTimeMillis()));
				/*
*/				time=new Date().getTime();    //获取现在的时间，单位是ms
        //which="2";   //2表示查询所有参数状态
        //which="1";    //设置which的值，表明是第position行的开关状态发生了改变
        if(IsShow)      //0.2s后判断是否有反馈，若没有则显示progressdialog，使页面不能点，然后在2.5秒后判断此次触发的progressdialog的显示是否已关闭，如果没有，则进行关闭，并将which置"100"
        {
            Timer timer = new Timer(true);     //定义定时器，定时执行关闭progressdialog命令，定时时长为2秒
            timer.schedule(new TimerTask(){
                public void run() {     //在新线程中执行
                    if(!which.equals("100"))
                    {
                        Message message = new Message();
                        message.what = 1;       //1表示要显示
                        handler2.sendMessage(message);
                        Timer timer1 = new Timer(true);     //定义定时器，定时执行关闭progressdialog命令，定时时长为2秒
                        timer1.schedule(new TimerTask(){
                            public void run() {     //在新线程中执行
                                if(!which.equals(100))
                                {
                                    Message message = new Message();
                                    message.what = 2;       //2表示要隐藏
                                    handler2.sendMessage(message);
                                }
                            }
                        } ,2000); //2.0s后判断是否关闭progressdialog，若没关闭，则进行关闭
                    }
                }
            } ,250); //0.25s后判断是否关闭progressdialog，若没关闭，则进行关闭

        }
        else   //如果选择不显示progressdialog
            which="100";   //直接允许下一条指令发送


        new Thread()   //新建子线程，发送命令
        {
            public void run(){
                DataOutputStream out;
                try
                {
                    out = new DataOutputStream(client.getOutputStream());
                    String toServer = "ab68"+order;    //指令，添加包头
                    toServer.replace(" ","");    //去掉空格
                    byte[] bt=null;
                    bt=Converts.HexString2Bytes(toServer);
                    String str=toServer+Converts.GetCRC(bt, 2, bt.length)+"0d0a";   //添加校验码和包尾
                    Log.i("Order","发送数据："+str);
                    byte[] bt1=Converts.HexString2Bytes(str);      //将完整的命令转换成十六进制
                    if(!client.isClosed())
                    {
                        out.write(bt1);
                        out.flush();
                        // out.close();   //关闭输出流
                    }
                }
                catch (Exception e) {			// 发送出错，证明TCP断开了连接，重新建立连接
                    try
                    {
                        InetAddress serverAddr = InetAddress.getByName(clientip);// TCPServer.SERVERIP
                        client = new Socket(serverAddr,2000);   //新建TCP连接
                        out=new DataOutputStream(client.getOutputStream());
                        new TCPServerThread().start();
                        String toServer = "ab68"+order;    //指令，添加包头
                        toServer.replace(" ","");    //去掉空格
                        byte[] bt=null;
                        bt=Converts.HexString2Bytes(toServer);
                        String str=toServer+Converts.GetCRC(bt, 2, bt.length)+"0d0a";   //添加校验码，和包尾
                        byte[] bt1=Converts.HexString2Bytes(str);      //将完整的命令转换成十六进制
                        if(!client.isClosed())
                        {
                            out.write(bt1);
                            out.flush();
                            // out.close();   //关闭输出流
                        }
                    }
                    catch (Exception ee){}

                }
            }
        }.start();
        //Log.i("Time","发送命令+"+String.valueOf(System.currentTimeMillis()));
        //}
    }
}
