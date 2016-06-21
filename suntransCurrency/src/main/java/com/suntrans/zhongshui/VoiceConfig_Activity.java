package com.suntrans.zhongshui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import convert.Converts;
import database.DbHelper;
import services.MainService;
import views.Switch;
import views.TouchListener;

/**
 * Created by 1111b on 2015/12/21.
 * 语音配置页面
 */
public class VoiceConfig_Activity extends Activity {
    private byte[] bits={(byte)0x01,(byte)0x02,(byte)0x04,(byte)0x08,(byte)0x10,(byte)0x20,(byte)0x40,(byte)0x80};     //从1到8只有一位是1，用于按位与计算，获取某一位的值
    private int flag_refresh=1;   //是否刷新，退出页面时清零
    private LinearLayout layout;   //返回
    private Switch switch_voice;  //语音开关
    private TextView tx_out,tx_in;  //外间和里间的数据
    private ArrayList<Map<String,String>> data=new ArrayList<Map<String,String>>();
    private ArrayList<Map<String,String>> data1=new ArrayList<Map<String, String>>();
    private String room="外间";   //当前显示的房间名称
    private String addr=Address.addr_out;
    private Toast toast;
    private String sub_voice="";   //更改的语音内容
    private String sub_channel="";   //更改的通道号
    private ListView list;   //列表
    private ProgressDialog progressdialog;      //定义”加载中。。。“的圆形滚动条弹出框
    private long time;   //触发progressdialog显示的时间
    private String state="1";   //外间的语音开关状态
    private String state1="1";  //里间的语音开关状态
    private byte warning_state=0;     //外间的报警开关的状态，因为修改语音开关时会涉及到报警开关的状态（存放在同一个寄存器中）
    private byte warning_state1=0;     //里间的报警开关的状态,
    private int flag=0;     //是否正在更改语音，如果flag=1，则表示正在更改语音
    public MainService.ibinder binder;  //用于Activity与Service通信
    private ServiceConnection con = new ServiceConnection() {
        @Override   //绑定服务成功后，调用此方法，获取返回的IBinder对象，可以用来调用Service中的方法
        public void onServiceConnected(ComponentName name, IBinder service) {
          //  Toast.makeText(getApplication(), "绑定成功！", Toast.LENGTH_SHORT).show();
            binder=(MainService.ibinder)service;   //activity与service通讯的类，调用对象中的方法可以实现通讯
            binder.sendOrder("ab68" + Address.addr_out + "f003 0100 0011", MainService.SIXSENSOR);   //读取外间语音开关状态
            try{Thread.sleep(310);}
            catch(Exception ex) {
                ex.printStackTrace();
            }
            binder.sendOrder("ab68" + Address.addr_in + "f003 0100 0011", MainService.SIXSENSOR);   //读取里间语音开关状态

           // binder.sendOrder(addr+"f003 0304 0001");
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
            if (count >10 && content.substring(8,10).equals("f0"))   //通过Fragment的handler将数据传过去
            {
                handler1.sendMessage(msg);
            }

        }
    };//广播接收器
    private Handler handler1=new Handler()
    {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.arg1 == 1) {    //刷新开关状态的显示
//                ((Adapter) grid.getAdapter()).notifyDataSetChanged();
                // grid.setAdapter(new Adapter());
                if(room.equals("外间"))
                    switch_voice.setState(state.equals("1")?true:false);
                else
                    switch_voice.setState(state1.equals("1")?true:false);
            } else   //解析数据
            {
                Map<String, Object> map = (Map<String, Object>) msg.obj;
                byte[] a = (byte[]) (map.get("data"));    //byte数组a即为客户端发回的数据，aa68 0006是单个开通道，aa68 0003是所有的通道
                // String ipaddr = (String) (map.get("ipaddr"));    //开关的IP地址
                String s = "";                       //保存命令的十六进制字符串
                for (int i = 0; i < msg.what; i++) {
                    String s1 = Integer.toHexString((a[i] + 256) % 256);   //byte转换成十六进制字符串(先把byte转换成0-255之间的非负数，因为java中的数据都是带符号的)
                    if (s1.length() == 1)
                        s1 = "0" + s1;
                    s = s + s1;
                }
                String crc;
                s = s.replace(" ", ""); //去掉空格
            //    Log.i("Order", "收到数据：" + s);
                int IsEffective = 0;    //指令是否有效，0表示无效，1表示有效；对于和第六感官通讯而言，包头为ab68的数据才有效
              //  crc= Converts.GetCRC(a, 4, msg.what - 2 - 2);    //获取返回数据的校验码，倒数第3、4位是验证码，倒数第1、2位是包尾0d0a
              //  &&crc.equals(s.substring(s.length()-8,s.length()))
                if(s.substring(0,10).equals("ab68"+Address.addr_out+"f0"))  //外间的数据
                    IsEffective=1;
                else if(s.substring(0,10).equals("ab68"+Address.addr_in+"f0"))   //里间的数据
                    IsEffective=2;
                if(IsEffective==1)    //外间
                {
                    if(s.substring(10,12).equals("03")||s.substring(10,12).equals("04"))  //读寄存器数据
                    {
                        if (s.substring(12, 14).equals("22")&&a.length>32)  //寄存器1，室内参数信息，包含报警开关状态，a[32]是存放开关状态的
                        {
                            warning_state=(byte)(a[32]&0x1f);                                   //温度
                            state=(a[32] & bits[5]) == bits[5] ? "1" : "0";     //外间语音开关状态
                            if(room.equals("外间"))
                                switch_voice.setState(state.equals("1") ? true : false);
                        }
                        else if(s.substring(12, 14).equals("02")&&s.substring(14,18).equals("0304")&&a.length>8)    //寄存器0304，报警开关状态
                        {
                            warning_state=(byte)(a[8]&0x1f);
                            state=(a[8] & bits[5]) == bits[5] ? "1" : "0";     //语音开关状态
                            if(room.equals("外间"))
                                switch_voice.setState(state.equals("1")?true:false);
                        }
                    }
                    else if(s.substring(10,12).equals("06"))   //控制命令返回
                    {
                        if(s.substring(12,14).equals("03"))   //更改语音、报警开关状态
                        {
                            warning_state=(byte)(a[9]&0x1f);
                            state=(a[9] & bits[5]) == bits[5] ? "1" : "0";     //语音开关状态
                            if(room.equals("外间"))
                                switch_voice.setState(state.equals("1")?true:false);
                        }
                        else if(s.substring(12,14).equals("05"))   //更改语音内容
                        {
                            if(!sub_voice.equals("")&&!sub_channel.equals(""))
                            {
                                DbHelper dh1 = new DbHelper(VoiceConfig_Activity.this, "IBMS", null, 1);
                                SQLiteDatabase db = dh1.getWritableDatabase();
                                ContentValues cv = new ContentValues();    //内容数组
                                //  Cursor cursor = db.query(true, "switchs_tb", new String[]{"Room","Name","Channel","Area","CID"}, "Area=?", new String[]{area}, null, null, null, null);
                                cv.put("VoiceName", sub_voice);
                                db.update("switchs_tb", cv, "Channel=? and Room=?", new String[]{sub_channel, room});
                                db.close();
                                data = new ArrayList<Map<String, String>>();
                                data1 = new ArrayList<Map<String, String>>();
                                DataInit();
                                ((Adapter) list.getAdapter()).notifyDataSetChanged();
                                toast.makeText(getApplication(),"配置成功!",Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
                else if(IsEffective==2)   //里间
                {
                    if(s.substring(10,12).equals("03")||s.substring(10,12).equals("04"))  //读寄存器数据
                    {
                        if (s.substring(12, 14).equals("22")&&a.length>32)  //寄存器1，室内参数信息，包含报警开关状态，a[32]是存放开关状态的
                        {
                            warning_state1=(byte)(a[32]&0x1f);                                   //温度
                            state1=(a[32] & bits[5]) == bits[5] ? "1" : "0";     //外间语音开关状态
                            if(room.equals("里间"))
                                switch_voice.setState(state1.equals("1") ? true : false);
                        }
                        else if(s.substring(12, 14).equals("02")&&s.substring(14,18).equals("0304")&&a.length>8)    //寄存器0304，报警开关状态
                        {
                            warning_state1=(byte)(a[8]&0x1f);
                            state1=(a[8] & bits[5]) == bits[5] ? "1" : "0";     //语音开关状态
                            if(room.equals("里间"))
                                switch_voice.setState(state1.equals("1")?true:false);
                        }
                    }
                    else if(s.substring(10,12).equals("06"))   //控制命令返回
                    {
                        if(s.substring(12,14).equals("03"))   //更改语音、报警开关状态
                        {
                            warning_state1=(byte)(a[9]&0x1f);
                            state1=(a[9] & bits[5]) == bits[5] ? "1" : "0";     //语音开关状态
                            if(room.equals("里间"))
                                switch_voice.setState(state1.equals("1")?true:false);
                        }
                        else if(s.substring(12,14).equals("05"))   //更改语音内容
                        {
                            if(!sub_voice.equals("")&&!sub_channel.equals(""))
                            {
                                DbHelper dh1 = new DbHelper(VoiceConfig_Activity.this, "IBMS", null, 1);
                                SQLiteDatabase db = dh1.getWritableDatabase();
                                ContentValues cv = new ContentValues();    //内容数组
                                //  Cursor cursor = db.query(true, "switchs_tb", new String[]{"Room","Name","Channel","Area","CID"}, "Area=?", new String[]{area}, null, null, null, null);
                                cv.put("VoiceName", sub_voice);
                                db.update("switchs_tb", cv, "Channel=? and Room=?", new String[]{sub_channel, room});
                                db.close();
                                data = new ArrayList<Map<String, String>>();
                                data1 = new ArrayList<Map<String, String>>();
                                DataInit();
                                ((Adapter) list.getAdapter()).notifyDataSetChanged();
                                toast.makeText(getApplication(),"配置成功!",Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
//                if (msg.what ==13)   //读取语音开关的状态
//                {
//                    if(s.substring(0, 8).equals("ab68"+Address.addr_out))  //外间
//                    {
//                        state = a[8] == 1 ? "1" : "0";
//                        if(room.equals("外间"))
//                            switch_voice.setState(state.equals("1")?true:false);
//                    }
//                    else if(s.substring(0, 8).equals("ab68"+Address.addr_in)) //里间
//                    {
//                        state1 = a[8] == 1 ? "1" : "0";
//                        if(room.equals("里间"))
//                            switch_voice.setState(state1.equals("1")?true:false);
//                    }
//                }
//                else if(msg.what==14)
//                {
//                    if(s.substring(12,14).equals("03")) { //控制语音开关的返回数据
//                        if (s.substring(0, 8).equals("ab68" + Address.addr_out))  //外间
//                        {
//                            state = (a[9] == 1 ? "1" : "0");
//                            if (room.equals("外间"))
//                                switch_voice.setState(state.equals("1") ? true : false);
//                        } else if (s.substring(0, 8).equals("ab68" + Address.addr_in)) //里间
//                        {
//                            state1 = (a[9] == 1 ? "1" : "0");
//                            if (room.equals("里间"))
//                                switch_voice.setState(state1.equals("1") ? true : false);
//                        }
//                    }
//                    else    //配置语音返回的数据
//                    {
//                        if(!sub_voice.equals("")&&!sub_channel.equals("")) {
//                            DbHelper dh1 = new DbHelper(VoiceConfig_Activity.this, "IBMS", null, 1);
//                            SQLiteDatabase db = dh1.getWritableDatabase();
//                            ContentValues cv = new ContentValues();    //内容数组
//                            //  Cursor cursor = db.query(true, "switchs_tb", new String[]{"Room","Name","Channel","Area","CID"}, "Area=?", new String[]{area}, null, null, null, null);
//                            cv.put("VoiceName", sub_voice);
//                            db.update("switchs_tb", cv, "Channel=? and Room=?", new String[]{sub_channel, room});
//                            db.close();
//                            data = new ArrayList<Map<String, String>>();
//                            data1 = new ArrayList<Map<String, String>>();
//                            DataInit();
//                            ((Adapter) list.getAdapter()).notifyDataSetChanged();
//                            toast.makeText(getApplication(),"配置成功!",Toast.LENGTH_SHORT).show();
//                        }
//                    }
//                }
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
                progressdialog = new ProgressDialog(VoiceConfig_Activity.this);    //初始化progressdialog
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
                    if(flag==1)
                    {
                        flag=0;
                        // Toast.makeText(getActivity(), "网络错误！", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            else   //如果是要显示progressdialog
            {
                progressdialog = new ProgressDialog(VoiceConfig_Activity.this);    //初始化progressdialog
                progressdialog.setCancelable(true);// 设置是否可以通过点击Back键取消
                progressdialog.setCanceledOnTouchOutside(false);// 设置在点击Dialog外是否取消Dialog进度条
                progressdialog.show();
                progressdialog.setContentView(R.layout.progressdialog);    //设置显示的内容
            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        Intent intent1=getIntent();
//        room = intent1.getStringExtra("room");  //房间.

        //绑定MainService
        Intent intent = new Intent(getApplicationContext(), MainService.class);    //指定要绑定的service
        bindService(intent, con, Context.BIND_AUTO_CREATE);   //绑定主service
        // 注册自定义动态广播消息。根据Action识别广播
        IntentFilter filter_dynamic = new IntentFilter();
        filter_dynamic.addAction("com.suntrans.beijing.RECEIVE");  //为IntentFilter添加Action，接收的Action与发送的Action相同时才会出发onReceive
        registerReceiver(broadcastreceiver, filter_dynamic);    //动态注册broadcast receiver
        setContentView(R.layout.voiceconfig);
        layout = (LinearLayout) findViewById(R.id.layout);
        tx_out = (TextView) findViewById(R.id.tx_out);
        tx_in = (TextView) findViewById(R.id.tx_in);
        switch_voice=(Switch) findViewById(R.id.switch_voice);
        switch_voice.setState(true);
        list = (ListView) findViewById(R.id.list);
        if(room.equals("里间")) {
            addr = Address.addr_in;
            tx_out.setTextColor(getResources().getColor(R.color.white));
            tx_out.setBackgroundColor(getResources().getColor(R.color.bg_action));
            tx_in.setTextColor(getResources().getColor(R.color.bg_action));
            tx_in.setBackgroundColor(getResources().getColor(R.color.white));
        }else
            addr=Address.addr_out;
        layout.setOnTouchListener(new TouchListener());
        layout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        tx_out.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tx_in.setTextColor(getResources().getColor(R.color.white));
                tx_in.setBackgroundColor(getResources().getColor(R.color.bg_action));
                tx_out.setTextColor(getResources().getColor(R.color.bg_action));
                tx_out.setBackgroundColor(getResources().getColor(R.color.white));
                room = "外间";
                addr=Address.addr_out;
                ((Adapter)list.getAdapter()).notifyDataSetChanged();
                switch_voice.setState(state.equals("1")?true:false);
            }
        });
        tx_in.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tx_out.setTextColor(getResources().getColor(R.color.white));
                tx_out.setBackgroundColor(getResources().getColor(R.color.bg_action));
                tx_in.setTextColor(getResources().getColor(R.color.bg_action));
                tx_in.setBackgroundColor(getResources().getColor(R.color.white));
                room="里间";
                addr=Address.addr_in;
                ((Adapter)list.getAdapter()).notifyDataSetChanged();
                switch_voice.setState(state1.equals("1") ? true : false);
            }
        });
        switch_voice.setOnChangeListener(new Switch.OnSwitchChangedListener() {
            @Override
            public void onSwitchChange(Switch switchView, boolean isChecked) {
                byte byt=(byte)((isChecked?32:0)+(room.equals("外间")?warning_state:warning_state1));   //开关状态内容
                binder.sendOrder("ab68"+addr + "f006 0304 " +"00"+ Converts.Bytes2HexString(new byte[]{byt}),MainService.SIXSENSOR);   //根据开关状态变化发送命令
                Timer timer = new Timer(true);     //定义定时器，定时执行关闭progressdialog命令，定时时长为2秒
                timer.schedule(new TimerTask() {
                    public void run() {     //在新线程中执行

                            Message message = new Message();
                            message.arg1 = 1;       //1表示要刷新显示
                            handler1.sendMessage(message);


                    }
                }, 2000); //2s后刷新显示
            }
        });
        DataInit();
        list.setAdapter(new Adapter());
    }
    @Override      //关闭时调用,解除Service的绑定，注册广播接收
    protected void onDestroy()
    {

        Log.i("Order", "controlinfo关闭");
        flag_refresh=0;
        try {
            unbindService(con);   //解除Service的绑定
            unregisterReceiver(broadcastreceiver);  //注销广播接收者
        }
        catch(Exception e){}
        super.onDestroy();
    }

    private void DataInit()
    {
        DbHelper dh1 = new DbHelper(VoiceConfig_Activity.this, "IBMS", null, 1);
        SQLiteDatabase db = dh1.getWritableDatabase();
        //外间
        Cursor cursor = db.query(true,"switchs_tb", new String[]{"Room", "Name","Channel","VoiceName","RSAddr"},"Room=?", new String[]{"外间"}, null, null, null, null);
        // Toast.makeText(getActivity().getApplication(),String.valueOf(cursor.getCount()),Toast.LENGTH_LONG).show();
        while (cursor.moveToNext())    //将所有的开关ip添加到ipaddr数组中
        {
            String Room=cursor.getString(0);  //获取房间名称
            String Name=cursor.getString(1);    //获取通道名称
            String Channel=cursor.getString(2); //获取通道号
            String VoiceName = cursor.getString(3);    //音频名称
            String RSAddr = cursor.getString(4);   //开关地址
         //   String VID=cursor.getString(4);  //ID
            Map<String, String> map=new HashMap<String,String>();
            map.put("Room", Room);
            map.put("Name", Name);
            map.put("Channel", Channel);
            map.put("VoiceName", VoiceName);
            map.put("RSAddr", RSAddr);
            data.add(map);
        }
        //里间
        cursor = db.query(true,"switchs_tb", new String[]{"Room", "Name","Channel","VoiceName","RSAddr"},"Room=?", new String[]{"里间"}, null, null, null, null);
        while (cursor.moveToNext())    //将所有的开关ip添加到ipaddr数组中
        {
            String Room=cursor.getString(0);  //获取房间名称
            String Name=cursor.getString(1);    //获取通道名称
            String Channel=cursor.getString(2); //获取通道号
            String VoiceName = cursor.getString(3);    //音频名称
            String RSAddr = cursor.getString(4);  //开关地址
       //     String VID=cursor.getString(4);  //ID
            Map<String, String> map=new HashMap<String,String>();
            map.put("Room", Room);
            map.put("Name", Name);
            map.put("Channel", Channel);
            map.put("VoiceName", VoiceName);
            map.put("RSAddr", RSAddr);
            data1.add(map);
        }
        db.close();

    }

    class Adapter extends BaseAdapter {
        @Override
        public int getCount() {       //列表的条目
            if (room.equals("外间"))
                return data.size();
            else
                return data1.size();
        }

        @Override
        public Object getItem(int position) {
            if (room.equals("外间"))
                return data.get(position);
            else
                return data1.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @SuppressLint("ResourceAsColor")
        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            // ViewHolder vh;
            // if(convertView==null) {
            convertView = LayoutInflater.from(getApplication()).inflate(R.layout.voiceconfig_listview, null);
            TextView tx_channel = (TextView) convertView.findViewById(R.id.tx_channel);
            TextView tx_name = (TextView) convertView.findViewById(R.id.tx_name);
            TextView tx_voice = (TextView) convertView.findViewById(R.id.tx_voice);
            Map<String, String> map;
            if(room.equals("外间"))
                map= data.get(position);
            else
                map = data1.get(position);
            final String Channel=map.get("Channel");  //通道号
            final String Name=map.get("Name");    //通道名称
            final String VoiceName=map.get("VoiceName");     //音频名称
            final String RSAddr = map.get("RSAddr");   //开关地址
            final int num_channel = Integer.valueOf(Channel);   //通道号，int型
            tx_channel.setText("通道"+Channel);
            tx_name.setText(Name);
            tx_voice.setText(VoiceName);
            tx_voice.setOnTouchListener(new TouchListener());
            tx_voice.setOnClickListener(new View.OnClickListener() {   //点击事件
                @Override
                public void onClick(View v) {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(VoiceConfig_Activity.this);
                    builder.setTitle("请选择语音指令");
                    final String[] list_name = new String[]{"客厅", "餐厅", "厨房", "洗手间",
                            "卫生间","阳台", "房间", "书房", "厕所", "门灯",
                            "车库", "路灯", "走廊", "壁灯", "客房",
                            "花园", "台灯", "储物室", "仓库", "阁楼", "地下室",
                            "楼梯", "水池", "泳池", "彩灯", "红灯", "绿灯", "蓝灯",
                            "电视", "空调", "会议室", "顶灯", "灯",
                            "开关", "前灯", "后灯", "办公室", "吊灯", "筒灯",
                            "射灯", "画面灯", "灯带"

                    };
                    //语音拼音
                    final String[] list_pinyin = new String[]{"ke ting", "can ting", "chu fang", "xi shou jian",
                            "wei sheng jian",   "yang tai", "fang jian", "shu fang", "ce suo", "men deng",
                            "che ku", "lu deng", "zou lang", "bi deng", "ke fang",
                            "hua yuan", "tai deng", "chu wu shi", "cang ku", "ge lou", "di xia shi",
                            "lou ti", "shui chi","yong chi", "cai deng", "hong deng", "lv deng", "lan deng",
                            "dian shi", "kong tiao", "hui yi shi", "ding deng", "deng",
                            "kai guan", "qian deng", "hou deng", "ban gong shi", "diao deng", "tong deng",
                            "she deng", "hua mian deng", "deng dai"

                    };
                    //语音序列号和语音长度
                    final String[] list_serial=new String[]{"0107","0208","0308","040c",
                            "050e","0608","0709","0808","0906","0a08",
                            "0b06","0c07","0d08","0e07","0f07",
                            "1008","1a08","120a","1307","1406","150a",
                            "1606","1708","1808","1908","1a09","1b07","1c08",
                            "1d08","1e09","1f0a","2009","2104",
                            "2208","2309","2408","250c","2609","2709",
                            "2808","290d","2a08"

                    };
                    builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // TODO Auto-generated method stub
                        }
                    });
                    builder.setItems(list_name, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            //点击后弹出窗口选择了第几项,which从0开始
                          //  Toast.makeText(getApplication(), String.valueOf(which), Toast.LENGTH_LONG).show();
                            flag=1;
                            if (RSAddr.equals("0001"))
                                binder.sendOrder("ab68" + addr + "f006 050" + (Channel.equals("10") ? "a" : Channel) + RSAddr.substring(2, 4) + "0" + (Channel.equals("10") ? "a" : Channel) + list_serial[which] + Converts.Bytes2HexString(list_pinyin[which].getBytes()),4);
                            else {
                                byte x=(byte)(num_channel+10);
                                String addr1=Converts.Bytes2HexString(new byte[]{x});
                                if(addr1.length()==1)
                                    addr1="0"+addr1;
                                binder.sendOrder("ab68" + addr + "f006 05" + addr1 + RSAddr.substring(2, 4) + "0" + (Channel.equals("10") ? "a" : Channel) + list_serial[which] + Converts.Bytes2HexString(list_pinyin[which].getBytes()),4);

                            }
                            sub_voice=list_name[which];
                            sub_channel=Channel;
                          //  Converts.Bytes2HexString(list_pinyin[2].getBytes())
                        }
                    });
                    builder.create().show();
                }

            });
            return convertView;
        }
    }
}
