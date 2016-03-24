package com.suntrans.zhongshui;

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
import android.widget.ImageButton;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

import services.MainService;

/**
 * Created by 石奋斗 on 2016/3/19.
 */
public class InfraredLearn_Activity extends Activity {
    public MainService.ibinder binder;  //用于Activity与Service通信
    private ImageButton btn_up,btn_down,btn_left,btn_right;    //上下左右四个按钮
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
    private Handler handler1=new Handler() {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    };

    @Override
    protected void onCreate(Bundle savedIntenceState)
    {
        super.onCreate(savedIntenceState);
        setContentView(R.layout.infraredlearn);
        Intent intent = new Intent(getApplicationContext(), MainService.class);   //要绑定的服务
        bindService(intent, con, Context.BIND_AUTO_CREATE);   //绑定service
        // 注册自定义动态广播消息。根据Action识别广播
        IntentFilter filter_dynamic = new IntentFilter();
        filter_dynamic.addAction("com.suntrans.beijing.RECEIVE");  //为IntentFilter添加Action，接收的Action与发送的Action相同时才会出发onReceive
        registerReceiver(broadcastreceiver, filter_dynamic);    //动态注册broadcast receiver
        btn_up = (ImageButton) findViewById(R.id.img_up);      //上
        btn_down = (ImageButton) findViewById(R.id.img_down);    //下
        btn_left = (ImageButton) findViewById(R.id.img_left);    //左
        btn_right = (ImageButton) findViewById(R.id.img_right);   //右

    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        try {
            unbindService(con);   //解绑service
            unregisterReceiver(broadcastreceiver);  //注销广播接收
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

    }


}
