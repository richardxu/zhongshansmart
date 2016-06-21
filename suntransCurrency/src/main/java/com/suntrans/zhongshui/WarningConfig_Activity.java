package com.suntrans.zhongshui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import convert.Converts;
import services.MainService;
import views.Switch;
import views.TouchListener;

/**
 * Created by 石奋斗 on 2016/3/5.
 */
public class WarningConfig_Activity  extends Activity{
    private byte[] bits={(byte)0x01,(byte)0x02,(byte)0x04,(byte)0x08,(byte)0x10,(byte)0x20,(byte)0x40,(byte)0x80};     //从1到8只有一位是1，用于按位与计算，获取某一位的值
    private String room = "外间";   //当前显示的房间名称
    private String addr=Address.addr_out;
    private String voice_state="0";   //外间语音开关状态，1代表开，0代表关
    private String voice_state1="0";    //里间语音开关状态,1代表开，0代表关
    private LinearLayout layout_back;   //返回
    private TextView tx_backto;      //恢复默认配置
    private TextView tx_out,tx_in;    //外间和里间按钮
    private ListView list;   //列表
    private Button bt_backto;   //恢复默认配置
    private long time=0;
    private int flag_refresh=1;   //是否刷新
    private String which = "100";
    private static String WRITE_STATE="1";   //写开关状态时的标志位
    private static String WRITE_VALUE = "2";   //写报警阈值时的标志位
    private static String INIT = "3";    //恢复默认时的标志位

    private ProgressDialog progressdialog;    //进度条
    public MainService.ibinder binder;  //用于Activity与Service通信
    private ArrayList<Map<String, String>> data = new ArrayList<>();     //外间的报警配置信息
    private ArrayList<Map<String, String>> data1 = new ArrayList<>();   //里间的报警配置信息
    DecimalFormat df1   = new DecimalFormat("0.0");    //保留一位小数
    DecimalFormat df3   = new DecimalFormat("0.000");   //保留三位小数
    private ServiceConnection con = new ServiceConnection() {
        @Override   //绑定服务成功后，调用此方法，获取返回的IBinder对象，可以用来调用Service中的方法
        public void onServiceConnected(ComponentName name, IBinder service) {
            //  Toast.makeText(getApplication(), "绑定成功！", Toast.LENGTH_SHORT).show();
            binder=(MainService.ibinder)service;   //activity与service通讯的类，调用对象中的方法可以实现通讯
            binder.sendOrder("ab68" + Address.addr_out + "f003 0100 0011", MainService.SIXSENSOR);   //读取外间报警开关状态
            try{Thread.sleep(310);}
            catch(Exception ex) {
                ex.printStackTrace();
            }
            binder.sendOrder("ab68" + Address.addr_out + "f003 0700 0005", MainService.SIXSENSOR);   //读取外间报警阈值
            try{Thread.sleep(310);}
            catch(Exception ex) {
                ex.printStackTrace();
            }
            binder.sendOrder("ab68" + Address.addr_in + "f003 0100 0011", MainService.SIXSENSOR);   //读取里间报警开关状态
            try{Thread.sleep(310);}
            catch(Exception ex) {
                ex.printStackTrace();
            }
            binder.sendOrder("ab68" + Address.addr_in + "f003 0700 0005", MainService.SIXSENSOR);//读取里间报警阈值
            try{Thread.sleep(310);}
            catch(Exception ex) {
                ex.printStackTrace();
            }
            new RefreshThread().start();   //开始刷新线程
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
            if (count >10 && content.substring(8,10).equals("f0"))   //通过handler将数据传过去
            {
                handler1.sendMessage(msg);
            }

        }
    };//广播接收器
    //新建刷新线程，刷新当前页面显示数据
    class RefreshThread extends Thread{
        @Override
        public void run(){

            while(flag_refresh==1) {
                if(which.equals("100"))
                    binder.sendOrder("ab68" + addr + "f003 0700 0005", MainService.SIXSENSOR);   //读取报警阈值
                try {
                    Thread.sleep(5000);
                }   //每隔5s读取一次报警阈值
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
    private Handler handler1=new Handler() {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
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
            s = s.replace(" ", ""); //去掉空格
            int IsEffective = 0;    //指令是否有效，0表示无效，1表示有效；对于和第六感官通讯而言，包头为ab68的数据才有效
            if (msg.what > 10) {
                if (s.substring(0, 10).equals("ab68"+Address.addr_out+"f0"))
                    IsEffective = 1;    //外间
                else if(s.substring(0, 10).equals("ab68"+Address.addr_in+"f0"))
                    IsEffective=2;   //里间
            }
            if(IsEffective==1) {
                if (s.substring(10, 12).equals("03")||s.substring(10,12).equals("04"))   //如果是读寄存器状态，则判断是读寄存器1（室内参数），还是寄存器0304（报警开关状态），还是寄存器7报警阈值
                {
                    if (s.substring(12, 14).equals("22")&&a.length>32)  //寄存器1，室内参数信息，包含报警开关状态，a[32]是存放开关状态的
                    {
                        String switch_shake = (a[32] & bits[0]) == bits[0] ? "1" : "0";     //振动
                        String switch_pm25 = (a[32] & bits[1]) == bits[1] ? "1" : "0";        //PM2.5
                        String switch_arofene = (a[32] & bits[2]) == bits[2] ? "1" : "0";      //甲醛
                        String switch_smoke = (a[32] & bits[3]) == bits[3] ? "1" : "0";        //烟雾
                        String switch_tmp = (a[32] & bits[4]) == bits[4] ? "1" : "0";          //温度
                        voice_state=(a[32] & bits[5]) == bits[5] ? "1" : "0";     //语音开关状态
                        data.get(0).put("State", switch_shake);
                        data.get(1).put("State", switch_pm25);
                        data.get(2).put("State", switch_arofene);
                        data.get(3).put("State", switch_smoke);
                        data.get(4).put("State", switch_tmp);
                        if(!which.equals(WRITE_STATE))   //如果正在更改状态，则先不更新页面
                            ((Adapter)list.getAdapter()).notifyDataSetChanged();
                    }
                    else if(s.substring(12, 14).equals("02")&&s.substring(14,18).equals("0304")&&a.length>8)    //寄存器0304，报警开关状态
                    {
                        String switch_shake = (a[8] & bits[0]) == bits[0] ? "1" : "0";     //振动
                        String switch_pm25 = (a[8] & bits[1]) == bits[1] ? "1" : "0";        //PM2.5
                        String switch_arofene = (a[8] & bits[2]) == bits[2] ? "1" : "0";      //甲醛
                        String switch_smoke = (a[8] & bits[3]) == bits[3] ? "1" : "0";        //烟雾
                        String switch_tmp = (a[8] & bits[4]) == bits[4] ? "1" : "0";          //温度
                        voice_state=(a[8] & bits[5]) == bits[5] ? "1" : "0";     //语音开关状态
                        data.get(0).put("State", switch_shake);
                        data.get(1).put("State", switch_pm25);
                        data.get(2).put("State", switch_arofene);
                        data.get(3).put("State", switch_smoke);
                        data.get(4).put("State", switch_tmp);
                        ((Adapter)list.getAdapter()).notifyDataSetChanged();
                    }
                    else if(s.substring(12,14).equals("0a")&&a.length>16)     //寄存器7，报警阈值
                    {
                        int tmp=(a[7]==2?1:-1)*(a[8]&0xff);   //温度报警值
                        int smoke=(a[9]==2?1:-1)*(a[10]&0xff);   //烟雾报警值
                        int arofene=(a[11]==2?1:-1)*(a[12]&0xff);   //甲醛报警值
                        int pm25=(a[13]==2?1:-1)*(a[14]&0xff);   //PM2.5报警阈值
                        int shake=(a[15]==2?1:-1)*(a[16]&0xff);   //振动报警阈值

                        data.get(0).put("Value", String.valueOf(shake));   //振动报警阈值
                        data.get(1).put("Value", String.valueOf(pm25));
                        data.get(2).put("Value", String.valueOf(arofene));
                        data.get(3).put("Value", String.valueOf(smoke));
                        data.get(4).put("Value", String.valueOf(tmp));
                        ((Adapter)list.getAdapter()).notifyDataSetChanged();
                    }
                }
                else if(s.substring(10,12).equals("06"))   //如果返回06，则是控制某个寄存器返回，或报警信息主动上报。
                {
                    if(s.substring(12,16).equals("0304")&&a.length>9)   //写开关状态的返回
                    {
                        if(which.equals(WRITE_STATE)) {
                            which = "100";
                            Message msg1 = new Message();
                            msg1.what=0;   //关闭dialogprogress
                            handler2.sendMessage(msg1);
                        }
                        String switch_shake = (a[9] & bits[0]) == bits[0] ? "1" : "0";     //振动
                        String switch_pm25 = (a[9] & bits[1]) == bits[1] ? "1" : "0";        //PM2.5
                        String switch_arofene = (a[9] & bits[2]) == bits[2] ? "1" : "0";      //甲醛
                        String switch_smoke = (a[9] & bits[3]) == bits[3] ? "1" : "0";        //烟雾
                        String switch_tmp = (a[9] & bits[4]) == bits[4] ? "1" : "0";          //温度
                        voice_state=(a[9] & bits[5]) == bits[5] ? "1" : "0";     //语音开关状态
                        data.get(0).put("State", switch_shake);
                        data.get(1).put("State", switch_pm25);
                        data.get(2).put("State", switch_arofene);
                        data.get(3).put("State", switch_smoke);
                        data.get(4).put("State", switch_tmp);
                        ((Adapter)list.getAdapter()).notifyDataSetChanged();
                    }

                    else if(s.substring(12,15).equals("070")&&a.length>9)      //修改某个报警阈值
                    {
                        if(which.equals(WRITE_VALUE))
                        {
                            which = "100";
                            Message msg1 = new Message();
                            msg1.what=0;   //关闭dialogprogress
                            handler2.sendMessage(msg1);
                        }
                        int item=Integer.valueOf(s.substring(15,16));   //判断是哪个参数，0代表振动，4代表温度。中间依次是烟雾、甲醛、PM2.5
                        data.get(item).put("Value", String.valueOf((a[8] == 2 ? 1 : -1) * (a[9] & 0xff)));
                        ((Adapter)list.getAdapter()).notifyDataSetChanged();
                    }
                }
                else if(s.substring(10,12).equals("10")&&a.length>11)   //修改多个寄存器的值
                {
                    if(s.substring(12,20).equals("07000005"))   //是写报警阈值寄存器，表示写成功了
                    {
                        if(which.equals(INIT)||which.equals(WRITE_VALUE)) {
                            which = "100";
                            Message msg1 = new Message();
                            msg1.what=0;   //关闭dialogprogress
                            handler2.sendMessage(msg1);
                        }
                        binder.sendOrder("ab68 " + addr + "f003 0700 0005", MainService.SIXSENSOR);   //读取报警阈值
                    }
                }


            }
            else if(IsEffective==2) {
                if (s.substring(10, 12).equals("03")||s.substring(10,12).equals("04"))   //如果是读寄存器状态，则判断是读寄存器1（室内参数），还是寄存器0304（报警开关状态），还是寄存器7报警阈值
                {
                    if (s.substring(12, 14).equals("22")&&a.length>32)  //寄存器1，室内参数信息，包含报警开关状态，a[32]是存放开关状态的
                    {
                        String switch_shake = (a[32] & bits[0]) == bits[0] ? "1" : "0";     //振动
                        String switch_pm25 = (a[32] & bits[1]) == bits[1] ? "1" : "0";        //PM2.5
                        String switch_arofene = (a[32] & bits[2]) == bits[2] ? "1" : "0";      //甲醛
                        String switch_smoke = (a[32] & bits[3]) == bits[3] ? "1" : "0";        //烟雾
                        String switch_tmp = (a[32] & bits[4]) == bits[4] ? "1" : "0";          //温度
                        voice_state1 = (a[32] & bits[5]) == bits[5] ? "1" : "0";
                        data1.get(0).put("State", switch_shake);
                        data1.get(1).put("State", switch_pm25);
                        data1.get(2).put("State", switch_arofene);
                        data1.get(3).put("State", switch_smoke);
                        data1.get(4).put("State", switch_tmp);
                        if(!which.equals(WRITE_STATE))   //如果正在更改状态，则先不更新页面
                             ((Adapter) list.getAdapter()).notifyDataSetChanged();
                    } else if (s.substring(12, 14).equals("02")&&a.length>8)    //寄存器0304，报警开关状态
                    {
                        String switch_shake = (a[8] & bits[0]) == bits[0] ? "1" : "0";     //振动
                        String switch_pm25 = (a[8] & bits[1]) == bits[1] ? "1" : "0";        //PM2.5
                        String switch_arofene = (a[8] & bits[2]) == bits[2] ? "1" : "0";      //甲醛
                        String switch_smoke = (a[8] & bits[3]) == bits[3] ? "1" : "0";        //烟雾
                        String switch_tmp = (a[8] & bits[4]) == bits[4] ? "1" : "0";          //温度
                        voice_state1 = (a[8] & bits[5]) == bits[5] ? "1" : "0";
                        data1.get(0).put("State", switch_shake);
                        data1.get(1).put("State", switch_pm25);
                        data1.get(2).put("State", switch_arofene);
                        data1.get(3).put("State", switch_smoke);
                        data1.get(4).put("State", switch_tmp);
                        ((Adapter) list.getAdapter()).notifyDataSetChanged();
                    } else if (s.substring(12, 14).equals("0a")&&a.length>16)     //寄存器7，报警阈值
                    {
                        int tmp = (a[7] == 2 ? 1 : -1) * (a[8] & 0xff);
                           //温度报警值
                        int smoke = (a[9] == 2 ? 1 : -1) * (a[10] & 0xff);
                           //烟雾报警值
                        int arofene = (a[11] == 2 ? 1 : -1) * (a[12] & 0xff);
                           //甲醛报警值
                        int pm25 = (a[13] == 2 ? 1 : -1) * (a[14] & 0xff);
                           //PM2.5报警阈值
                        int shake = (a[15] == 2 ? 1 : -1) * (a[16] & 0xff);
                           //振动报警阈值

                        data1.get(0).put("Value", String.valueOf(shake));   //振动报警阈值
                        data1.get(1).put("Value", String.valueOf(pm25));
                        data1.get(2).put("Value", String.valueOf(arofene));
                        data1.get(3).put("Value", String.valueOf(smoke));
                        data1.get(4).put("Value", String.valueOf(tmp));
                        ((Adapter) list.getAdapter()).notifyDataSetChanged();
                    }
                }
                else if(s.substring(10,12).equals("06"))   //如果返回06，则是控制某个寄存器返回，或报警信息主动上报。
                {
                    if(s.substring(12,16).equals("0304")&&a.length>9)   //写开关状态的返回
                    {
                        if(which.equals(WRITE_STATE)) {
                            which = "100";
                            Message msg1 = new Message();
                            msg1.what=0;   //关闭dialogprogress
                            handler2.sendMessage(msg1);
                        }
                        String switch_shake = (a[9] & bits[0]) == bits[0] ? "1" : "0";     //振动
                        String switch_pm25 = (a[9] & bits[1]) == bits[1] ? "1" : "0";        //PM2.5
                        String switch_arofene = (a[9] & bits[2]) == bits[2] ? "1" : "0";      //甲醛
                        String switch_smoke = (a[9] & bits[3]) == bits[3] ? "1" : "0";        //烟雾
                        String switch_tmp = (a[9] & bits[4]) == bits[4] ? "1" : "0";          //温度
                        voice_state1=(a[9] & bits[5]) == bits[5] ? "1" : "0";     //语音开关状态
                        data1.get(0).put("State", switch_shake);
                        data1.get(1).put("State", switch_pm25);
                        data1.get(2).put("State", switch_arofene);
                        data1.get(3).put("State", switch_smoke);
                        data1.get(4).put("State", switch_tmp);
                        ((Adapter)list.getAdapter()).notifyDataSetChanged();
                    }

                    else if(s.substring(12,15).equals("070")&&a.length>9)      //修改某个报警阈值
                    {
                        if(which.equals(WRITE_VALUE))
                        {
                            which = "100";
                            Message msg1 = new Message();
                            msg1.what=0;   //关闭dialogprogress
                            handler2.sendMessage(msg1);
                        }
                        int item=Integer.valueOf(s.substring(15,16));   //判断是哪个参数，0代表振动，4代表温度。中间依次是烟雾、甲醛、PM2.5
                        data1.get(item).put("Value", String.valueOf((a[8] == 2 ? 1 : -1) * (a[9] & 0xff)));
                        ((Adapter)list.getAdapter()).notifyDataSetChanged();
                    }
                }
                else if(s.substring(10,12).equals("10")&&a.length>11)   //修改多个寄存器的值
                {
                    if(s.substring(12,20).equals("07000005"))   //是写报警阈值寄存器，表示写成功了
                    {
                        if(which.equals(INIT)||which.equals(WRITE_VALUE)) {
                            which = "100";
                            Message msg1 = new Message();
                            msg1.what=0;   //关闭dialogprogress
                            handler2.sendMessage(msg1);
                        }
                        binder.sendOrder("ab68 " + addr + "f003 0700 0005", MainService.SIXSENSOR);   //读取报警阈值
                    }
                }
            }

        }
    };
    private Handler handler2 = new Handler(){
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
                progressdialog = new ProgressDialog(WarningConfig_Activity.this);    //初始化progressdialog
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
                        ((Adapter)list.getAdapter()).notifyDataSetChanged();
                     //    Toast.makeText(WarningConfig_Activity.this, "网络错误！", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            else   //如果是要显示progressdialog
            {
                progressdialog = new ProgressDialog(WarningConfig_Activity.this);    //初始化progressdialog
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
//        Intent intent1 = getIntent();
//        room = intent1.getStringExtra("room");  //房间.

        //绑定MainService
        Intent intent = new Intent(getApplicationContext(), MainService.class);    //指定要绑定的service
        bindService(intent, con, Context.BIND_AUTO_CREATE);   //绑定主service
        // 注册自定义动态广播消息。根据Action识别广播
        IntentFilter filter_dynamic = new IntentFilter();
        filter_dynamic.addAction("com.suntrans.beijing.RECEIVE");  //为IntentFilter添加Action，接收的Action与发送的Action相同时才会出发onReceive
        registerReceiver(broadcastreceiver, filter_dynamic);    //动态注册broadcast receiver
        setContentView(R.layout.warningconfig);
        layout_back = (LinearLayout) findViewById(R.id.layout_back);
        tx_out = (TextView) findViewById(R.id.tx_out);
        tx_in = (TextView) findViewById(R.id.tx_in);
        list = (ListView) findViewById(R.id.list);
        tx_backto = (TextView) findViewById(R.id.tx_backto);
        tx_backto.setOnTouchListener(new TouchListener());
        tx_backto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {    //恢复默认配置
                final AlertDialog.Builder builder = new AlertDialog.Builder(WarningConfig_Activity.this);
                builder.setTitle("确认将" + room + "报警阈值恢复默认？");
                builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        which = INIT;
                        Timer timer = new Timer(true);     //定义定时器，定时执行关闭progressdialog命令，定时时长为2秒
                        timer.schedule(new TimerTask() {
                            public void run() {     //在新线程中执行
                                if (!which.equals("100")) {
                                    Message message = new Message();
                                    message.what = 1;       //1表示要显示
                                    handler2.sendMessage(message);
                                }
                                Timer timer1 = new Timer(true);     //定义定时器，定时执行关闭progressdialog命令，定时时长为2秒
                                timer1.schedule(new TimerTask() {
                                    public void run() {     //在新线程中执行
                                        if (!which.equals(100)) {
                                            Message message = new Message();
                                            message.what = 2;       //2表示要隐藏
                                            handler2.sendMessage(message);
                                        }
                                    }
                                }, 2500); //2.5s后判断是否关闭progressdialog，若没关闭，则进行关闭
                            }
                        }, 250); //0.25s后判断是否关闭progressdialog，若没关闭，则进行关闭
                        byte byt = 0;
//                        if (room.equals("外间")) {
//                            byt = (byte) ((voice_state.equals("1") ? 1 : 0) * 32);   //语音开关状态
//                        } else {
//                            byt = (byte) ((voice_state1.equals("1") ? 1 : 0) * 32);   //语音开关状态
//                        }
                        //发送命令，修改报警开关状态
                       // binder.sendOrder("ab68 " + addr + "f006 0304 00" + Converts.Bytes2HexString(new byte[]{byt}), MainService.SIXSENSOR);
//                        try {
//                            Thread.sleep(100);
//                        } catch (Exception ex) {
//                            ex.printStackTrace();
//                        }
                        //发送命令，修改报警阈值
                        binder.sendOrder("ab68" + addr + "f010 0700 0005 0a 0232 0208 0214 0207 0205", MainService.SIXSENSOR);
                    }
                });
                builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                    }
                });
                builder.create().show();


            }
        });
//        bt_backto = (Button) findViewById(R.id.bt_backto);
//        bt_backto.setOnTouchListener(new TouchListener());
//        bt_backto.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                DataInit();
//                ((Adapter)list.getAdapter()).notifyDataSetChanged();
//            }
//        });

        if(room.equals("里间")) {
            addr = Address.addr_in;
            tx_out.setTextColor(getResources().getColor(R.color.white));
            tx_out.setBackgroundColor(getResources().getColor(R.color.bg_action));
            tx_in.setTextColor(getResources().getColor(R.color.bg_action));
            tx_in.setBackgroundColor(getResources().getColor(R.color.white));
        }else
            addr=Address.addr_out;
        layout_back.setOnTouchListener(new TouchListener());
        layout_back.setOnClickListener(new View.OnClickListener() {
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
                list.setAdapter(new Adapter());
               // ((Adapter)list.getAdapter()).notifyDataSetChanged();
//                switch_voice.setState(state.equals("1")?true:false);
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
                addr = Address.addr_in;
                list.setAdapter(new Adapter());
//                ((Adapter)list.getAdapter()).notifyDataSetChanged();
//                switch_voice.setState(state1.equals("1") ? true : false);
            }
        });

        DataInit();
        list.setAdapter(new Adapter());

    }
    @Override      //关闭时调用,解除Service的绑定，注册广播接收
    protected void onDestroy()
    {

        Log.i("Order", "controlinfo关闭");
        flag_refresh=0;   //结束刷新线程
        try {
            unbindService(con);   //解除Service的绑定
            unregisterReceiver(broadcastreceiver);  //注销广播接收者
        }
        catch(Exception e){}
        super.onDestroy();
    }

    //数据初始化
    private void DataInit() {
        data = new ArrayList<>();
        data1 = new ArrayList<>();
        //外间第六感官关于报警信息的初始化
        Map<String, String> map1 = new HashMap<>();
        map1.put("Name", "振动");
        map1.put("Value", "0");   //报警阈值
        map1.put("State", "0");   //报警开关的状态，1表示开，0表示关
        data.add(map1);

        Map<String, String> map2 = new HashMap<>();
        map2.put("Name", "PM2.5");
        map2.put("Value", "0");   //报警阈值
        map2.put("State", "0");   //报警开关的状态，1表示开，0表示关
        data.add(map2);

        Map<String, String> map3 = new HashMap<>();
        map3.put("Name", "甲醛");
        map3.put("Value", "0");   //报警阈值
        map3.put("State", "0");   //报警开关的状态，1表示开，0表示关
        data.add(map3);

        Map<String, String> map4 = new HashMap<>();
        map4.put("Name", "烟雾");
        map4.put("Value", "0");   //报警阈值
        map4.put("State", "0");   //报警开关的状态，1表示开，0表示关
        data.add(map4);

        Map<String, String> map5 = new HashMap<>();
        map5.put("Name", "温度");
        map5.put("Value", "0");   //报警阈值
        map5.put("State", "0");   //报警开关的状态，1表示开，0表示关
        data.add(map5);

        //里间第六感官报警信息相关数据初始化
        Map<String, String> map6 = new HashMap<>();
        map6.put("Name", "振动");
        map6.put("Value", "0");   //报警阈值
        map6.put("State", "0");   //报警开关的状态，1表示开，0表示关
        data1.add(map6);

        Map<String, String> map7 = new HashMap<>();
        map7.put("Name", "PM2.5");
        map7.put("Value", "0");   //报警阈值
        map7.put("State", "0");   //报警开关的状态，1表示开，0表示关
        data1.add(map7);

        Map<String, String> map8 = new HashMap<>();
        map8.put("Name", "甲醛");
        map8.put("Value", "0");   //报警阈值
        map8.put("State", "0");   //报警开关的状态，1表示开，0表示关
        data1.add(map8);

        Map<String, String> map9 = new HashMap<>();
        map9.put("Name", "烟雾");
        map9.put("Value", "0");   //报警阈值
        map9.put("State", "0");   //报警开关的状态，1表示开，0表示关
        data1.add(map9);

        Map<String, String> map10 = new HashMap<>();
        map10.put("Name", "温度");
        map10.put("Value", "0");   //报警阈值
        map10.put("State", "0");   //报警开关的状态，1表示开，0表示关
        data1.add(map10);
    }

    class Adapter extends BaseAdapter{

        @Override
        public int getCount() {
            if(room.equals("外间"))
                return data.size();
            else
                return data1.size();
        }

        @Override
        public Object getItem(int position) {
            if(room.equals("外间"))
                return data.get(position);
            else
                return data1.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if(convertView==null)
                convertView= LayoutInflater.from(getApplication()).inflate(R.layout.warningconfig_listview, null);
            TextView name = (TextView) convertView.findViewById(R.id.name);
            TextView value = (TextView) convertView.findViewById(R.id.value);
            Button bt = (Button) convertView.findViewById(R.id.bt);
            final Switch switch_warning = (Switch) convertView.findViewById(R.id.switch_warning);
            Map<String,String> map;
            if(room.equals("外间"))
                map=data.get(position);
            else
                map = data1.get(position);
            name.setText(map.get("Name"));
            int int_value = Integer.valueOf(map.get("Value"));   //int型阈值

            double double_value=int_value;  //乘以系数以后的实际阈值
            String str_double_value="";  //double型数据转成String型数据
            if(position==0)
            {
                double_value = int_value * 0.1;
                str_double_value=df1.format(double_value);
            }
            else if(position==2) {
                double_value = int_value * 0.01;
                str_double_value=df3.format(double_value);
            }
            else if(position==4) {
                double_value = int_value * 1;
                str_double_value=String.valueOf((int)double_value);
            }
            else {
                double_value = int_value * 100;
                str_double_value=String.valueOf((int)double_value);
            }
            value.setText(str_double_value);
            final String str_name = map.get("Name");
            final String str_value = str_double_value;
            switch_warning.setState((map.get("State").equals("1")) ? true : false);
            //value点击监听，弹出对话框让用户输入要更改的数值
            value.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LayoutInflater factory = LayoutInflater.from(WarningConfig_Activity.this);
                    final View view = factory.inflate(R.layout.warning_input, null);
                    final AlertDialog.Builder builder = new AlertDialog.Builder(WarningConfig_Activity.this);
                    builder.setTitle("请输入"+str_name+"报警阈值：");
                    final EditText  tx1= (EditText) view.findViewById(R.id.tx1);   //整型数值
                    tx1.setHint(str_value);
//                    if(position==0)
//                        tx2.setText("*0.1");
//                    else if(position==2)
//                        tx2.setText("*0.001");
//                    else if(position==4)
//                        tx2.setText("*1");
//                    else
//                        tx2.setText("*100");

                   // tx1.setText(str_value);
                   // tx1.setSelection(str_value.length());
                    builder.setView(view);
                    builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            if(!(tx1.getText().toString().length()==0||tx1.getText().toString()==null)) {   //判断输入不为空
                                String str=tx1.getText().toString();   //获取输入字符串的内容
                                Pattern pattern = Pattern.compile("^[-+]?(([0-9]+)([.]([0-9]+))?|([.]([0-9]+))?)$");
                                Matcher isNum = pattern.matcher(str);
                                if( isNum.matches() ) {    //判断是否为数字
                                    double new_value =Double.valueOf(tx1.getText().toString());   //用户输入的新的报警阈值,position正好对应寄存器070x中的x

                                    int new_value_int=0;
                                    if(position==0)
                                        new_value_int=(int)(new_value*10);
                                    else if(position==2)
                                        new_value_int = (int) (new_value * 100);
                                    else if(position==4)
                                        new_value_int=(int)new_value;
                                    else
                                        new_value_int = (int) (new_value * 0.01);
                                    String[] values = new String[5];  //阈值数组
                                    for (int k = 0; k < 5; k++) {
                                        if(k==position)   //要更改的值
                                        {
                                            int new_value_abs=Math.abs(new_value_int);
                                            values[k]= (new_value_int > 0 ? "02" : "00") +Converts.Bytes2HexString(new byte[]{(byte) ((new_value_abs> 255) ? 255 : new_value_abs)});
                                        }
                                        else
                                        {
                                            if(room.equals("外间")) {
                                                int p =Integer.valueOf( data.get(k).get("Value"));
                                                int p_abs = Math.abs(p);   //p的绝对值
                                                values[k] =(p > 0 ? "02" : "00") +Converts.Bytes2HexString(new byte[]{(byte) ((p_abs > 255) ? 255 : p_abs)});
                                            }
                                            else if(room.equals("里间"))
                                            {
                                                int p =Integer.valueOf( data1.get(k).get("Value"));
                                                int p_abs = Math.abs(p );   //p的绝对值
                                                values[k] =(p > 0 ? "02" : "00") +Converts.Bytes2HexString(new byte[]{(byte) ((p_abs > 255) ? 255 : p_abs)});
                                            }
                                        }
                                    }
                                    String order_value="";
                                    for(int j=4;j>=0;j--)
                                        order_value+=values[j];
                                    String order = "ab68" + addr + "f010 0700 0005 0a"+order_value;
                                    which = WRITE_VALUE;   //wich=2，代表更改单个报警阈值
                                    Timer timer = new Timer(true);     //定义定时器，定时执行关闭progressdialog命令，定时时长为2秒
                                    timer.schedule(new TimerTask() {
                                        public void run() {     //在新线程中执行
                                            if (!which.equals("100")) {
                                                Message message = new Message();
                                                message.what = 1;       //1表示要显示
                                                handler2.sendMessage(message);
                                            }
                                            Timer timer1 = new Timer(true);     //定义定时器，定时执行关闭progressdialog命令，定时时长为2秒
                                            timer1.schedule(new TimerTask() {
                                                public void run() {     //在新线程中执行
                                                    if (!which.equals(100)) {
                                                        Message message = new Message();
                                                        message.what = 2;       //2表示要隐藏
                                                        handler2.sendMessage(message);
                                                    }
                                                }
                                            }, 2500); //2.5s后判断是否关闭progressdialog，若没关闭，则进行关闭
                                        }
                                    }, 250); //0.25s后判断是否关闭progressdialog，若没关闭，则进行关闭

                                    binder.sendOrder(order, MainService.SIXSENSOR);
                                }
                                else
                                {
                                    Toast.makeText(getApplicationContext(),"输入非法字符！",Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    });
                    builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {

                        }
                    });
                    builder.create().show();
                }
            });
            //开关更改监听
            switch_warning.setOnChangeListener(new Switch.OnSwitchChangedListener() {
                @Override
                public void onSwitchChange(Switch switchView, boolean isChecked) {

                    which=WRITE_STATE;
                    Timer timer = new Timer(true);     //定义定时器，定时执行关闭progressdialog命令
                    timer.schedule(new TimerTask() {
                        public void run() {     //在新线程中执行
                            if (!which.equals("100")) {
                                Message message = new Message();
                                message.what = 1;       //1表示要显示
                                handler2.sendMessage(message);
                            }
                            Timer timer1 = new Timer(true);     //定义定时器，定时执行关闭progressdialog命令，定时时长为2秒
                            timer1.schedule(new TimerTask() {
                                public void run() {     //在新线程中执行
                                    if (!which.equals(100)) {
                                        Message message = new Message();
                                        message.what = 2;       //2表示要隐藏
                                        handler2.sendMessage(message);
                                    }
                                }
                            }, 2500); //2.5s后判断是否关闭progressdialog，若没关闭，则进行关闭
                        }
                    }, 250); //0.25s后判断是否关闭progressdialog，若没关闭，则进行关闭
                    if(room.equals("外间")) {
                        //因为0304寄存器存放的是语音、温度、烟雾、甲醛、PM2.5、振动的开关状态，所以值要把六个开关的状态都写进去
                        byte byt= (byte) ((voice_state.equals("1")?1:0)*32);   //语音开关状态
                        for(int i=0;i<=4;i++)
                        {
                            if(i==position)    //如果是需要改变的开关，数值写入与原状态相反的值
                                byt+=(data.get(i).get("State").equals("1")?0:1)*Math.pow(2,i);
                            else
                                byt+=(data.get(i).get("State").equals("1")?1:0)*Math.pow(2,i);
                        }

                        String order = "ab68" + addr + "f006 0304" + "00" + Converts.Bytes2HexString(new byte[]{byt});
                        binder.sendOrder(order, MainService.SIXSENSOR);
                    }
                    else
                    {
                        //因为0304寄存器存放的是语音、温度、烟雾、甲醛、PM2.5、振动的开关状态，所以值要把六个开关的状态都写进去
                        byte byt= (byte) ((voice_state1.equals("1")?1:0)*32);   //语音开关状态
                        for(int i=0;i<=4;i++)
                        {
                            if(i==position)    //如果是需要改变的开关，数值写入与原状态相反的值
                                byt+=(data1.get(i).get("State").equals("1")?0:1)*Math.pow(2,i);
                            else
                                byt+=(data1.get(i).get("State").equals("1")?1:0)*Math.pow(2,i);
                        }

                        String order = "ab68" + addr + "f006 0304" + "00" + Converts.Bytes2HexString(new byte[]{byt});
                        binder.sendOrder(order, MainService.SIXSENSOR);
                    }
                }
            });

//            if(position==4) {    //如果是最后一行，则显示最后的恢复默认配置按钮
//                bt.setVisibility(View.VISIBLE);
//              //  bt.setOnTouchListener(new TouchListener());
//                bt.setOnClickListener(new View.OnClickListener() {
//                    @Override
//                    public void onClick(View v) {   //恢复默认配置
//                        //DataInit();
//                        if (room.equals("外间")) {
//                            data = new ArrayList<Map<String, String>>();  //先把数据清零，再添加初始值
//                            //外间第六感官关于报警信息的初始化
//                            Map<String, String> map1 = new HashMap<>();
//                            map1.put("Name", "振动");
//                            map1.put("Value", "50");   //报警阈值
//                            map1.put("State", "1");   //报警开关的状态，1表示开，0表示关
//                            data.add(map1);
//
//                            Map<String, String> map2 = new HashMap<>();
//                            map2.put("Name", "PM2.5");
//                            map2.put("Value", "50");   //报警阈值
//                            map2.put("State", "1");   //报警开关的状态，1表示开，0表示关
//                            data.add(map2);
//
//                            Map<String, String> map3 = new HashMap<>();
//                            map3.put("Name", "甲醛");
//                            map3.put("Value", "50");   //报警阈值
//                            map3.put("State", "1");   //报警开关的状态，1表示开，0表示关
//                            data.add(map3);
//
//                            Map<String, String> map4 = new HashMap<>();
//                            map4.put("Name", "烟雾");
//                            map4.put("Value", "50");   //报警阈值
//                            map4.put("State", "1");   //报警开关的状态，1表示开，0表示关
//                            data.add(map4);
//
//                            Map<String, String> map5 = new HashMap<>();
//                            map5.put("Name", "温度");
//                            map5.put("Value", "50");   //报警阈值
//                            map5.put("State", "1");   //报警开关的状态，1表示开，0表示关
//                            data.add(map5);
//
//                        } else {
//                            data1 = new ArrayList<Map<String, String>>();
//                            //里间第六感官报警信息相关数据初始化
//                            Map<String, String> map6 = new HashMap<>();
//                            map6.put("Name", "振动");
//                            map6.put("Value", "50");   //报警阈值
//                            map6.put("State", "1");   //报警开关的状态，1表示开，0表示关
//                            data1.add(map6);
//
//                            Map<String, String> map7 = new HashMap<>();
//                            map7.put("Name", "PM2.5");
//                            map7.put("Value", "50");   //报警阈值
//                            map7.put("State", "1");   //报警开关的状态，1表示开，0表示关
//                            data1.add(map7);
//
//                            Map<String, String> map8 = new HashMap<>();
//                            map8.put("Name", "甲醛");
//                            map8.put("Value", "50");   //报警阈值
//                            map8.put("State", "1");   //报警开关的状态，1表示开，0表示关
//                            data1.add(map8);
//
//                            Map<String, String> map9 = new HashMap<>();
//                            map9.put("Name", "烟雾");
//                            map9.put("Value", "50");   //报警阈值
//                            map9.put("State", "1");   //报警开关的状态，1表示开，0表示关
//                            data1.add(map9);
//
//                            Map<String, String> map10 = new HashMap<>();
//                            map10.put("Name", "温度");
//                            map10.put("Value", "50");   //报警阈值
//                            map10.put("State", "1");   //报警开关的状态，1表示开，0表示关
//                            data1.add(map10);
//
//                        }
//                        Adapter.this.notifyDataSetChanged();
//                    }
//                });
//            }
//            else
//                bt.setVisibility(View.GONE);
            return convertView;
        }
    }
}
