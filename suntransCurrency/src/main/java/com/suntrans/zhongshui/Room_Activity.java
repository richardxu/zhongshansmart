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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.MediaStore;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshGridView;
import com.readystatesoftware.viewbadger.BadgeView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import database.DbHelper;
import services.MainService;
import views.TouchListener;

/**
 * Created by 石奋斗 on 2016/3/26.
 */
public class Room_Activity extends Activity {
    private LinearLayout layout_back;   //返回
    private TextView title;  //标题
    private String room = "";   //当前房间名，由intent传入
    private int result_code=0;
    private ArrayList<String> rsaddrs=new ArrayList<String>();   //存放所有的开关地址
    // private ArrayList<String> rsaddrs1=new ArrayList<String>();   //存放里间包含的所有的开关地址
    private int isVisible=0;    //本页面是否可见
    private GridView grid;    //下拉GridView中的GridView
    private PullToRefreshGridView gridView;  //下拉GridView
    private byte[] bits={(byte)0x01,(byte)0x02,(byte)0x04,(byte)0x08,(byte)0x10,(byte)0x20,(byte)0x40,(byte)0x80};     //从1到8只有一位是1，用于按位与计算，获取某一位的值
    private ArrayList<Map<String, Object>> data=new ArrayList<Map<String, Object>>();    //列表显示的内容，开关的通道和状态，所有房间的开关
    //   private ArrayList<Map<String, Object>> data1=new ArrayList<Map<String,Object>>();    //列表显示的内容，开关的通道和状态，里间
    private DisplayMetrics displayMetrics = new DisplayMetrics();
    private Map<String,Integer> Mainstate=new HashMap<String,Integer>();   //外间总开关的状态，为0表示关
    private int Mainstate1=0;   //里间总开关的状态，为0表示关
    private ProgressDialog progressdialog;      //定义”加载中。。。“的圆形滚动条弹出框
    private long time;   //触发progressdialog显示的时间
    // private String addr=Address.addr_out;   //外间地址，默认0004
    private String which="100";
    private BadgeView[] badge=new BadgeView[500];   //开关图片右上角显示开关状态的图标，默认最多为500个
    public MainService.ibinder binder;  //用于Activity与Service通信
    private ServiceConnection con = new ServiceConnection() {
        @Override   //绑定服务成功后，调用此方法，获取返回的IBinder对象，可以用来调用Service中的方法
        public void onServiceConnected(ComponentName name, IBinder service) {
            //  Toast.makeText(getApplication(), "绑定成功！", Toast.LENGTH_SHORT).show();
            binder = (MainService.ibinder) service;   //activity与service通讯的类，调用对象中的方法可以实现通讯
            binder.sendOrder("aa68" + Address.addr_out + "0001 03 0100 0007", MainService.SWITCH);   //读取开关状态
            try {
                Thread.sleep(310);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            binder.sendOrder("aa68" + Address.addr_out + "0002 03 0100 0007", MainService.SWITCH);   //读取开关状态
            // binder.sendOrder(addr+"f003 0304 0001");
            //    Log.v("Time", "绑定后时间：" + String.valueOf(System.currentTimeMillis()));
        }

        @Override   //service因异常而断开的时候调用此方法
        public void onServiceDisconnected(ComponentName name) {
            Toast.makeText(getApplication(), "网络错误！", Toast.LENGTH_SHORT).show();

        }
    };
    ;   ///用于绑定activity与service
    //新建广播接收器，接收服务器的数据并解析，根据第六感官的地址和开关的地址将数据转发到相应的Fragment
    private BroadcastReceiver broadcastreceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

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
            if (count > 10)   //通过Fragment的handler将数据传过去
            {
                handler1.sendMessage(msg);
            }

        }
    };//广播接收器
    public Handler handler1=new Handler() {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.arg1 == 1) {    //房间进行了切换，刷新List的显示
//                ((Adapter) grid.getAdapter()).notifyDataSetChanged();
//                if(((Main_Activity)getActivity()).flag_room.equals("外间"))
//                    addr= Address.addr_out;
//                else
//                    addr=Address.addr_in;
//                if(isVisible==1)   //如果页面可见，则进行刷新
//                    gridView.setRefreshing();
                // grid.setAdapter(new Adapter());
            }
            else   //解析数据
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
                //   String crc=Converts.GetCRC(a, 2, msg.what-2-2);    //获取返回数据的校验码，倒数第3、4位是验证码，倒数第1、2位是包尾0d0a
                s = s.replace(" ", ""); //去掉空格
                //  Log.i("Order", "收到数据：" + s);
                int IsEffective = 0;    //指令是否有效，0表示无效，1表示有效；对于和第六感官通讯而言，包头为ab68的数据才有效
                if (msg.what > 13) {
                    if (s.substring(0, 4).equals("aa69"))
                        IsEffective = 1;    //数据有效
                }
                if(IsEffective==1)   //外间的开关数据
                {
                    final String return_addr=s.substring(4,12);   //返回数据的开关地址
                    if (s.substring(12, 14).equals("03"))   //如果是读寄存器状态，解析出开关状态
                    {
                        if (s.substring(14, 16).equals("0e")||s.substring(14,16).equals("07"))
                        {
                            String[] states={"0","0","0","0","0","0","0","0","0","0"};   //十个通道的状态，state[0]对应1通道
                            for(int i=0;i<8;i++)   //先获取前八位的开关状态
                            {
                                states[i]=((a[9]&bits[i])==bits[i])?"1":"0";   //1-8通道
                            }
                            for(int i=0;i<2;i++)
                            {
                                states[i+8]=((a[8]&bits[i])==bits[i])?"1":"0";  //9、10通道
                            }
                            Mainstate.put(return_addr,((a[8]&bits[2])==bits[2])?1:0);
                            for(int i=0;i<data.size();i++)
                            {
                                if(data.get(i).get("RSAddr").toString().equals(return_addr))
                                {
                                    data.get(i).put("State",states[Integer.valueOf(data.get(i).get("Channel").toString().equals("a")?"10":data.get(i).get("Channel").toString())-1]);
                                }
                            }
                            ((Adapter)grid.getAdapter()).notifyDataSetChanged();

                        }
                    }
                    else if(s.substring(12,14).equals("06"))   //单个通道状态发生改变
                    {
                        int k=0;         //k是通道号
                        int state=Integer.valueOf(s.substring(21, 22));  //开关状态，1代表打开，0代表关闭
                        if(s.substring(17,18).equals("a"))
                            k=10;
                        else
                            k=Integer.valueOf(s.substring(17, 18));   //通道号,int型
                        if(k==0)                                          //如果通道号为0，则是总开关
                        {
                            Mainstate.put(return_addr,state); //更新总开关数组中的开关状态
                            if(state==0)   //如果总开关关了，那肯定所有通道都关了
                            {
                                for(int i=0;i<data.size();i++)
                                {
                                    if(data.get(i).get("RSAddr").toString().equals(return_addr))
                                    {
                                        data.get(i).put("State","0");
                                    }
                                }
                            }
                        }
                        else     //如果通道号不为0，则更改data中的状态，并更新
                        {
                            for(int i=0;i<data.size();i++)
                            {
                                if(data.get(i).get("RSAddr").toString().equals(return_addr)&&data.get(i).get("Channel").toString().equals(String.valueOf(k)))
                                {
                                    data.get(i).put("State",state==1?"1":"0");
                                }
                            }
                        }
                        if(String.valueOf(k).equals(which))
                        {
                            which="100";
                            Message message = new Message();
                            message.what =0;       //0表示要隐藏
                            handler2.sendMessage(message);
                        }
                        ((Adapter)grid.getAdapter()).notifyDataSetChanged();
                    }
                }
                //  Log.i("Order", "外间总开关：" + String.valueOf(Mainstate) + "里间总开关" + String.valueOf(Mainstate1));
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
                progressdialog = new ProgressDialog(Room_Activity.this);    //初始化progressdialog
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
                        // Toast.makeText(getActivity(), "网络错误！", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            else   //如果是要显示progressdialog
            {
                progressdialog = new ProgressDialog(Room_Activity.this);    //初始化progressdialog
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
        setContentView(R.layout.room1);
        Intent intent1 = getIntent();
        room = intent1.getStringExtra("Room");   //获取房间名

        Intent intent = new Intent(getApplicationContext(), MainService.class);   //要绑定的服务
        bindService(intent, con, Context.BIND_AUTO_CREATE);   //绑定service
        // 注册自定义动态广播消息。根据Action识别广播
        IntentFilter filter_dynamic = new IntentFilter();
        filter_dynamic.addAction("com.suntrans.beijing.RECEIVE");  //为IntentFilter添加Action，接收的Action与发送的Action相同时才会出发onReceive
        registerReceiver(broadcastreceiver, filter_dynamic);    //动态注册broadcast receiver

        layout_back = (LinearLayout) findViewById(R.id.back);  //返回键
        layout_back.setOnTouchListener(new TouchListener());
        layout_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        title = (TextView) findViewById(R.id.tx_title);
        title.setText(room);
        DataInit();
        gridView = (PullToRefreshGridView) findViewById(R.id.gridview);
        gridView.setMode(PullToRefreshBase.Mode.PULL_FROM_START);   //只下拉刷新
        grid = gridView.getRefreshableView();
        gridView.setOnRefreshListener(new PullToRefreshBase.OnRefreshListener<GridView>() {
            @Override
            public void onRefresh(PullToRefreshBase<GridView> refreshView) {
                String label = DateUtils.formatDateTime(getApplicationContext(), System.currentTimeMillis(),
                        DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_ALL);
                Log.v("Time", "开始刷新的时间" + String.valueOf(System.currentTimeMillis()));
                // Update the LastUpdatedLabel
                refreshView.getLoadingLayoutProxy().setLastUpdatedLabel("上次刷新：" + label);
                new GetDataTask().execute();   //执行刷新任务
            }
        });

        grid.setAdapter(new Adapter());

    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        try {
            unbindService(con);   //解绑service
            unregisterReceiver(broadcastreceiver);  //注销广播接收
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    ///下拉刷新处理的函数。
    private class GetDataTask extends AsyncTask<Void, Void, String>
    {
        // 后台处理部分
        @Override
        protected String doInBackground(Void... params)
        {
            for (String switch_addr : rsaddrs) {
                String order = "aa68" + switch_addr + "03 0100 0007";
                binder.sendOrder(order,2);   //发送命令
                try {
                    Thread.sleep(60);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            //which="2";   //2表示查询所有参数状态
//            String addr;
//            if(((Main_Activity)getActivity()).flag_room.equals("外间"))
//                addr="0002";
//            else
//                addr="0003";

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
                Toast.makeText(getApplicationContext(), "加载失败！", Toast.LENGTH_SHORT).show();
            }
            // Call onRefreshComplete when the list has been refreshed.
            gridView.onRefreshComplete();   //表示刷新完成

            super.onPostExecute(result);//这句是必有的，AsyncTask规定的格式
        }
    }

    private void DataInit()    //数据初始化，十个开关的名称和通道号
    {
        data = new ArrayList<>();
        DbHelper dh1 = new DbHelper(Room_Activity.this, "IBMS", null, 1);
        SQLiteDatabase db = dh1.getWritableDatabase();
        //外间
        Cursor cursor = db.query(true, "switchs_tb", new String[]{"Room", "Name", "State", "Channel", "Image", "CID", "RSAddr", "MainAddr", "Editable"}, "Area like ?", new String[]{"%"+room+"%"}, null, null, "CID asc", null);
        // Toast.makeText(getActivity().getApplication(),String.valueOf(cursor.getCount()),Toast.LENGTH_LONG).show();
        while (cursor.moveToNext())    //将所有的开关ip添加到ipaddr数组中
        {
            String Room = cursor.getString(0);  //获取房间名称
            String Name = cursor.getString(1);    //获取通道名称
            String Channel = cursor.getString(3); //获取通道号
            String State = cursor.getString(2);    //开关状态
            byte[] in = cursor.getBlob(4);     //获取图片
            String Cid = cursor.getString(5);    //通道ID
            String RSAddr = cursor.getString(7) + cursor.getString(6);   //第六感地址+开关地址
            String Editable = cursor.getString(8);   //是否可以编辑
            Bitmap bitmap = BitmapFactory.decodeByteArray(in, 0, in.length);   //转化成bitmap格式
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("Room", Room);
            map.put("Name", Name);
            map.put("State", State);
            map.put("Channel", Channel);
            map.put("Image", bitmap);
            map.put("CID", Cid);
            map.put("RSAddr", RSAddr);
            map.put("Editable", Editable);
            data.add(map);
            //bitmap.recycle();   //回收
        }

        cursor = db.query(true, "switchs_tb", new String[]{"RSAddr", "MainAddr"}, "Area=?", new String[]{room}, null, null, null, null);
        while (cursor.moveToNext())
            rsaddrs.add( cursor.getString(1)+cursor.getString(0) );   //所有该房间内开关485地址

        cursor.close();
        for(String s:rsaddrs)
            Mainstate.put(s,0);   //开关总通道的状态

//        Mainstate.put(Address.addr_out+"0001", 0);
//        Mainstate.put(Address.addr_out+"0002", 0);
//        Mainstate.put(Address.addr_in+"0001", 0);
        db.close();
    }  //数据初始化结束

    class Adapter extends BaseAdapter {
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

        @SuppressLint("ResourceAsColor")
        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            // ViewHolder vh;
            // if(convertView==null) {
            convertView = LayoutInflater.from(getApplication()).inflate(R.layout.room1_gridview, null);
            // vh=new ViewHolder();
            ImageView image = (ImageView) convertView.findViewById(R.id.image);
            TextView tx_name = (TextView) convertView.findViewById(R.id.name);
            LinearLayout layout = (LinearLayout) convertView.findViewById(R.id.layout1);
            // convertView.setTag(vh);
            //}
            // vh=(ViewHolder)convertView.getTag();
            Map<String, Object> map;
            map= data.get(position);


            //vh.name.setText(map.get("Name"));
            final String state=map.get("State").toString();
            final String name = map.get("Name").toString();
            final String channel = map.get("Channel").toString();
            final String CID=map.get("CID").toString();
            final String RSAddr = map.get("RSAddr").toString();
            final String Editable = map.get("Editable").toString();
            Bitmap bmp=(Bitmap)map.get("Image");
            bmp=convert.Converts.toRoundCorner(bmp, 20);  //实现图片的圆角
            image.setImageBitmap(bmp);
            if(state.equals("0"))     //如果开关状态为关
            {

                badge[position]=new BadgeView(getApplicationContext(),layout);
                badge[position].setWidth(convert.Converts.dip2px(getApplicationContext(), 7));  //设置宽度为7dip
                badge[position].setHeight(convert.Converts.dip2px(getApplicationContext(), 7)); //设置高度为7dip
                badge[position].setBackgroundResource(R.drawable.offdot);    //设置图标
                badge[position].setBadgePosition(BadgeView.POSITION_TOP_RIGHT); //设置显示的位置，右上角
                badge[position].show();

                tx_name.setText(name);
            }
            else            //如果开关状态为开
            {
                badge[position]=new BadgeView(getApplicationContext(),layout);
                badge[position].setWidth(convert.Converts.dip2px(getApplicationContext(), 7));  //设置宽度为7dip
                badge[position].setHeight(convert.Converts.dip2px(getApplicationContext(), 7)); //设置高度为7dip
                badge[position].setBackgroundResource(R.drawable.ondot);    //设置图标
                badge[position].setBadgePosition(BadgeView.POSITION_TOP_RIGHT); //设置显示的位置，右上角
                badge[position].show();

                tx_name.setText(name);
            }
            //设置点击监听
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(which.equals("100")&&Editable.equals("1")) {
                        which = channel;    //设置which的值，表明是第channel个通道发生了改变
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


                        if (state.equals("0") &&Mainstate.get(RSAddr) == 0 ) //如果是要打开开关，并且总开关没有打开，则先打开总开关
                        {
                            new Thread() {
                                public void run() {
                                    binder.sendOrder("aa68"+RSAddr+"06 0300 0001",2);
                                    try {
                                        Thread.sleep(200);
                                    } catch (Exception e) {
                                    }
                                    binder.sendOrder("aa68"+RSAddr+"06 030" + (channel.equals("10") ? "a" : channel) + " 000" + (state.equals("0") ? "1" : "0"),2);
                                }
                            }.start();
                        }
                        else
                            binder.sendOrder("aa68"+RSAddr + "06 030" + (channel.equals("10") ? "a" : channel) + " 000" + (state.equals("0") ? "1" : "0"),2);

                    }
                }
            });
            // 设置长点击监听
            convertView.setOnLongClickListener(new View.OnLongClickListener(){    //设置长点击事件

                @Override
                public boolean onLongClick(View v) {
                    // TODO Auto-generated method stub
                    final AlertDialog.Builder builder = new AlertDialog.Builder(Room_Activity.this);
                    builder.setTitle("编辑开关信息：");
                    builder.setItems(new String[]{"修改名称","更换图标"}, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            //点击后弹出窗口选择了第几项
                            //Toast.makeText(getApplicationContext(),"你点击了第"+which+"个图标",Toast.LENGTH_SHORT).show();
                            switch(which)
                            {
                                case 0:  //修改名称
                                {
                                    LayoutInflater factory = LayoutInflater.from(Room_Activity.this);
                                    final View view = factory.inflate(R.layout.hoursedialog, null);
                                    final AlertDialog.Builder builder = new AlertDialog.Builder(Room_Activity.this);
                                    builder.setTitle("请输入通道名称：");
                                    final EditText tx1= (EditText) view.findViewById(R.id.tx1);
                                    tx1.setText(name);
                                    tx1.setSelection(name.length());
                                    builder.setView(view);
                                    builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {

                                            String New_Name=tx1.getText().toString();
                                            DbHelper dh1=new DbHelper(Room_Activity.this,"IBMS",null,1);
                                            SQLiteDatabase db = dh1.getWritableDatabase();
                                            ContentValues cv = new ContentValues();    //内容数组
                                            //  Cursor cursor = db.query(true, "switchs_tb", new String[]{"Room","Name","Channel","Area","CID"}, "Area=?", new String[]{area}, null, null, null, null);
                                            cv.put("Name", New_Name);
                                            db.update("switchs_tb", cv, "CID=?", new String[]{CID});
                                            //   Cursor cursor = db.query(true, "room_tb", new String[]{"RID","Name"}, null, null, null, null, null, null, null);
                                            data=new ArrayList<Map<String, Object>>();
                                            DataInit();

                                            Adapter.this.notifyDataSetChanged();   //刷新
                                            Toast.makeText(getApplicationContext(),"修改成功！",Toast.LENGTH_SHORT).show();


                                        }
                                    });
                                    builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {

                                        }
                                    });
                                    builder.create().show();

                                    break;
                                }
                                case 1:  //  更换图标
                                {
                                    builder.setTitle("更换图标：");
                                    builder.setItems(new String[]{"本地图库","拍照","取消"}, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            //点击后弹出窗口选择了第几项
                                            //Toast.makeText(getApplicationContext(),"你点击了第"+which+"个图标",Toast.LENGTH_SHORT).show();
                                            switch(which)
                                            {
                                                case 0:    //选择本地图库
                                                {
                                                    result_code=position;
                                                    //打开图库
                                                    Intent i = new Intent(
                                                            Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                                                    startActivityForResult(i,10000); //请求码为10000，  用来区分图片是裁剪前的还是裁剪后的
                                                    break;
                                                }
                                                case 1:    //选择拍照
                                                {
                                                    result_code=position;
                                                    //拍照
                                                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                                                    intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
                                                    startActivityForResult(intent, 10001);  //请求码为10001， 用来区分图片是裁剪前的还是裁剪后的
                                                    break;
                                                }
                                                case 2://点击取消
                                                {
                                                    break;
                                                }
                                                default:break;
                                            }}});
                                    builder.create().show();
                                    break;
                                }
//                                case 2:    //删除开关
//                                {
//                                    DbHelper dh1=new DbHelper(getActivity(),"IBMS",null,1);
//                                    SQLiteDatabase db = dh1.getWritableDatabase();
//                                    ContentValues cv = new ContentValues();    //内容数组
//                                    cv.put("Area","无");
//                                    cv.put("Name","无");
//                                    db.update("switchs_tb", cv, "CID=?", new String[]{CID});
//                                    Cursor cursor = db.query(true, "switchs_tb", new String[]{"IPAddr","Name","Channel","Type","Area","CID"}, "Area=?", new String[]{area}, null, null, null, null);
//                                    data=new ArrayList<Map<String, Object>>();
//                                    if(area.equals("所有房间"))
//                                    {
//                                        cursor = db.query(true, "switchs_tb", new String[]{"IPAddr","Name","Channel","Type","Area","CID","Image"}, "Area!=? and Name!=?", new String[]{"无","无"}, null, null, null, null);
//                                        while (cursor.moveToNext())    //将所有的开关ip添加到ipaddr数组中
//                                        {
//                                            String ipaddr=cursor.getString(0);  //获取开关ip地址
//                                            String Name=cursor.getString(1);    //获取通道名称
//                                            String Channel=cursor.getString(2); //获取通道号
//                                            String Type=cursor.getString(3);  //获取通道类型
//                                            String Area=cursor.getString(4);  //获取区域名称
//                                            String CID=cursor.getString(5);  //获取ID
//                                            byte[] in = cursor.getBlob(6);     //获取图片
//                                            Bitmap bitmap = BitmapFactory.decodeByteArray(in, 0, in.length);   //转化成bitmap格式
//                                            Map<String, Object> map=new HashMap<String,Object>();
//                                            map.put("IPAddr", ipaddr);
//                                            map.put("Name", Name);
//                                            map.put("State", "false");
//                                            map.put("Channel", Channel);
//                                            map.put("Type",Type);
//                                            map.put("Area",Area);
//                                            map.put("CID",CID);
//                                            map.put("Image", bitmap);
//                                            data.add(map);
//                                        }
//
//                                    }
//                                    else
//                                    {
//                                        cursor = db.query(true, "switchs_tb", new String[]{"IPAddr","Name","Channel","Type","Area","CID","Image"}, "Area=?", new String[]{area}, null, null, null, null);
//                                        while (cursor.moveToNext())    //将所有的开关ip添加到ipaddr数组中
//                                        {
//                                            String ipaddr=cursor.getString(0);  //获取开关ip地址
//                                            String Name=cursor.getString(1);    //获取通道名称
//                                            String Channel=cursor.getString(2); //获取通道号
//                                            String Type=cursor.getString(3);  //获取通道类型
//                                            String Area=cursor.getString(4);  //获取区域名称
//                                            String CID=cursor.getString(5);   //获取ID号
//                                            byte[] in = cursor.getBlob(6);     //获取图片
//                                            Bitmap bitmap = BitmapFactory.decodeByteArray(in, 0, in.length);   //转化成bitmap格式
//                                            Map<String, Object> map=new HashMap<String,Object>();
//                                            map.put("IPAddr", ipaddr); //IP地址
//                                            map.put("Name", Name);    //名称
//                                            map.put("State", "false");//开关状态
//                                            map.put("Channel", Channel);//通道号
//                                            map.put("Type",Type);     //通道类型
//                                            map.put("Area",Area);   //区域名
//                                            map.put("CID",CID);     //ID号
//                                            map.put("Image",bitmap);
//                                            data.add(map);
//                                        }
//                                    }
//                                    Adapter.this.notifyDataSetChanged();   //刷新
//                                    Toast.makeText(getActivity().getApplicationContext(),"删除成功！",Toast.LENGTH_SHORT).show();
//                                    break;
//                                }
                                default:break;
                            }
                        }
                    });
                    builder.show();
                    return false;
                }});
            return convertView;
        }

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data1) {
        super.onActivityResult(requestCode, resultCode, data1);
        if (resultCode != RESULT_CANCELED)
        {
            if(requestCode==10000||requestCode==10001)//如果是刚刚选择完，还未裁剪，则跳转到裁剪的activity
            {
                if (data1 != null) {
                    //取得返回的Uri,基本上选择照片的时候返回的是以Uri形式，但是在拍照中有得机子呢Uri是空的，所以要特别注意
                    Uri mImageCaptureUri = data1.getData();
                    //返回的Uri不为空时，那么图片信息数据都会在Uri中获得。如果为空，那么我们就进行下面的方式获取
                    if (mImageCaptureUri != null) {
                        Bitmap image;
                        try {
                            //这个方法是根据Uri获取Bitmap图片的静态方法
                            image = MediaStore.Images.Media.getBitmap(getContentResolver(), mImageCaptureUri);
                            if (image != null) {
                                startPhotoZoom(mImageCaptureUri);    //打开裁剪activity
                            }
                        } catch (Exception e) {
                            Log.i("IBM","URI出错"+e.toString());
                        }
                    }
                    else {
                        Bundle extras = data1.getExtras();
                        Bitmap image=null;
                        if (extras != null) {
                            //这里是有些拍照后的图片是直接存放到Bundle中的所以我们可以从这里面获取Bitmap图片
                            image = extras.getParcelable("data");
                        }
                        // 判断存储卡是否可以用，可用进行存储
                        String state = Environment.getExternalStorageState();
                        if (state.equals(Environment.MEDIA_MOUNTED)) {
                            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                            File tempFile = new File(path, "image.jpg");
                            FileOutputStream b = null;
                            try {
                                b = new FileOutputStream(tempFile);
                                image.compress(Bitmap.CompressFormat.JPEG, 100, b);// 把数据写入文件
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            } finally {
                                try {
                                    b.flush();
                                    b.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                            startPhotoZoom(Uri.fromFile(tempFile));
                        }
                        else {
                            Toast.makeText(getApplicationContext(), "未找到存储卡，无法存储照片！", Toast.LENGTH_SHORT).show();
                        }
                    }

                }
            }
            else   //如果是裁剪后返回，调用的回调方法
            {
                if (data1 != null) {
                    //取得返回的Uri,基本上选择照片的时候返回的是以Uri形式，但是在拍照中有得机子呢Uri是空的，所以要特别注意
                    Uri mImageCaptureUri = data1.getData();
                    //返回的Uri不为空时，那么图片信息数据都会在Uri中获得。如果为空，那么我们就进行下面的方式获取
                    if (mImageCaptureUri != null) {
                        Log.i("IBM","URI不为空"+mImageCaptureUri.toString());
                        Bitmap image;
                        try {
                            //这个方法是根据Uri获取Bitmap图片的静态方法
                            image = MediaStore.Images.Media.getBitmap(getContentResolver(), mImageCaptureUri);
                            if (image != null) {
                                data.get(result_code).put("Image",image);
                                DbHelper dh1=new DbHelper(Room_Activity.this,"IBMS",null,1);
                                SQLiteDatabase db = dh1.getWritableDatabase();
                                ContentValues cv = new ContentValues();    //内容数组
                                ByteArrayOutputStream os = new ByteArrayOutputStream();
                                image.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
                                cv.put("Image", os.toByteArray());
                                db.update("switchs_tb", cv, "CID=?", new String[]{data.get(result_code).get("CID").toString()});
                                ((Adapter)grid.getAdapter()).notifyDataSetChanged();   //刷新
                            }
                        } catch (Exception e) {
                            Log.i("IBM","URI出错"+e.toString());
                        }
                    }
                    else {
                        Bundle extras = data1.getExtras();
                        Log.i("IBM",extras.toString());
                        if (extras != null) {
                            //这里是有些拍照后的图片是直接存放到Bundle中的所以我们可以从这里面获取Bitmap图片
                            Bitmap image = extras.getParcelable("data");
                            if (image != null) {
                                data.get(result_code).put("Image",image);
                                DbHelper dh1=new DbHelper(Room_Activity.this,"IBMS",null,1);
                                SQLiteDatabase db = dh1.getWritableDatabase();
                                ContentValues cv = new ContentValues();    //内容数组
                                ByteArrayOutputStream os = new ByteArrayOutputStream();
                                image.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
                                cv.put("Image", os.toByteArray());
                                db.update("switchs_tb", cv, "CID=?", new String[]{data.get(result_code).get("CID").toString()});
                                ((Adapter)grid.getAdapter()).notifyDataSetChanged();   //刷新
                            }
                        }
                    }
                }
            }

        }
        else   //如果点击了取消，则判断是不是裁剪的activity，若是则返回图片选择或拍照的页面，若不是则不进行操作
        {
            if((requestCode==10000||requestCode==10001))//如果是在选择图片中，点击了取消，不进行操作
            {}
            else
            {
                //如果是在裁剪的页面选择了取消，则打开图库
                Intent i = new Intent(
                        Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(i,10000); //请求码为10000，  用来区分图片是裁剪前的还是裁剪后的


            }
        }
    }
    /**
     * 裁剪图片方法实现
     * @param uri
     */
    public void startPhotoZoom(Uri uri) {
        Intent intent = new Intent("com.android.camera.action.CROP");
        intent.setDataAndType(uri, "image/*");
        // 设置裁剪
        intent.putExtra("crop", "true");
        intent.putExtra("scale", true);// 去黑边
        intent.putExtra("scaleUpIfNeeded", true);// 去黑边
        // aspectX aspectY 是宽高的比例
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        // outputX outputY 是裁剪图片宽高
        intent.putExtra("outputX", convert.Converts.dip2px(getApplicationContext(), 90));
        intent.putExtra("outputY", convert.Converts.dip2px(getApplicationContext(), 90));
        intent.putExtra("return-data", false);
        startActivityForResult(intent, result_code);
    }

}
