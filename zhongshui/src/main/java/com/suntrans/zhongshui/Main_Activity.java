package com.suntrans.zhongshui;

import convert.Converts;
import services.MainService;
import views.ControlFragment;
import views.ElecinfoFragment;
import views.IViewPager;
import views.ParameterFragment;
import views.RoomFragment;
import views.TouchListener;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;

import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;

import java.util.HashMap;
import java.util.Map;

//OnGestureListener   手势接口
public class Main_Activity extends FragmentActivity {
    private LinearLayout layout_room,layout_title;
    private PopupWindow mainpopwindow = null;
    private int Flag = 0;    //主程序是否监测到menu键
    public IViewPager vPager;    //自定义ViewPager控件
    private TextView tx_control,tx_parameter,tx_elecinfo,tx_out,tx_in;   //标签栏的三个标题，和标题栏的两个标题
    private ImageView img_control,img_parameter,img_elecinfo;    //标签栏对应的三个图标
    private ImageView setting;   //设置按钮
    private TextView tx_edit;
    private ImageView img_info;   //  查看在线参数图标
    private ControlFragment control;
    private ElecinfoFragment elecinfo;
    private int IsVisiable=0;    //当前页面是否可见
    private RoomFragment room;
    public String flag_room="外间";     //当前选择的是外间还是里间
    private ParameterFragment parameter;    //共2个Fragment页面
    private long mExitTime;        //用于连按两次返回键退出      中间的时间判断
    public MainService.ibinder binder;  //用于Activity与Service通信
    private ServiceConnection con = new ServiceConnection() {
        @Override   //绑定服务成功后，调用此方法，获取返回的IBinder对象，可以用来调用Service中的方法
        public void onServiceConnected(ComponentName name, IBinder service) {
          //  Toast.makeText(getApplication(), "绑定成功！", Toast.LENGTH_SHORT).show();
            binder=(MainService.ibinder)service;   //activity与service通讯的类，调用对象中的方法可以实现通讯
            binder.sendOrder("aa68"+Address.addr_out+"000103 0100 0007",2);
            try {
                Thread.sleep(60);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            binder.sendOrder("aa68"+Address.addr_out+"000203 0100 0007",2);
            try {
                Thread.sleep(60);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            binder.sendOrder("aa68"+Address.addr_in+"000103 0100 0007",2);
            Log.v("Time", "绑定后时间：" + String.valueOf(System.currentTimeMillis()));
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
           // map.put("ipaddr", "a");   //客户端的IP地址
            Message msg = new Message();   //新建Message，用于向handler传递数据
            msg.what = count;   //数组有效数据长度
            msg.obj = map;  //接收到的数据数组
            msg.arg1=0;    //通知Fragment房间有没有发生变化，=0则房间未变化，=1则房间进行了切换

            Message msg1=new Message();
            msg1.what = count;
            msg1.obj=map;
            msg1.arg1=0;
            if(count>10)   //通过Fragment的handler将数据传过去
            {
                if (content.substring(8, 10).equals("f0") || content.substring(8, 10).equals("F0"))  //第六感的信息
                    parameter.handler1.sendMessage(msg);
                else {   //开关和电表的信息
                    control.handler1.sendMessage(msg);
                    elecinfo.handler1.sendMessage(msg1);
                }
            }

        }
    };//广播接收器
    @Override
    protected void onResume(){   //页面可见
        super.onResume();
        IsVisiable=1;
    }
    @Override
    protected void onStop(){
        super.onStop();
        IsVisiable=0;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);      //设置activity布局文件
        //绑定MainService
        Intent intent = new Intent(getApplicationContext(), MainService.class);    //指定要绑定的service
        bindService(intent, con, Context.BIND_AUTO_CREATE);   //绑定主service
        // 注册自定义动态广播消息。根据Action识别广播
        IntentFilter filter_dynamic = new IntentFilter();
        filter_dynamic.addAction("com.suntrans.beijing.RECEIVE");  //为IntentFilter添加Action，接收的Action与发送的Action相同时才会出发onReceive
        registerReceiver(broadcastreceiver, filter_dynamic);    //动态注册broadcast receiver
        //实例化控件，并初始化控件
        layout_room = (LinearLayout) findViewById(R.id.layout_room);      //文字标题
        layout_title = (LinearLayout) findViewById(R.id.layout_title);     //房间名称标题
        vPager=(IViewPager)findViewById(R.id.vPager);
        tx_control=(TextView)findViewById(R.id.tx_control);
        tx_parameter=(TextView)findViewById(R.id.tx_parameter);
        tx_elecinfo = (TextView) findViewById(R.id.tx_elecinfo);
        tx_out=(TextView)findViewById(R.id.tx_out);
        tx_in=(TextView)findViewById(R.id.tx_in);
        img_control=(ImageView)findViewById(R.id.img_control);
        img_parameter=(ImageView)findViewById(R.id.img_parameter);
        img_elecinfo = (ImageView) findViewById(R.id.img_elecinfo);
        tx_edit = (TextView) findViewById(R.id.tx_edit);
        setting=(ImageView)findViewById(R.id.img_setting);

        setting.setOnTouchListener(new TouchListener());
        setting.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Flag = 0;
                Showpopwindow();   //显示（或隐藏）菜单栏
            }
        });
        tx_edit.setOnTouchListener(new TouchListener());
        tx_edit.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.putExtra("Room", flag_room);
                intent.setClass(Main_Activity.this, Edit_Activity.class);
                startActivity(intent);   //跳转到编辑页面
            }
        });
        img_control.clearColorFilter();    //先清除之前的滤镜效果
        img_control.setColorFilter(0xffEB0000);                //红色

        vPager.setAdapter(new MyPagerAdapter(getSupportFragmentManager()));   //设置viewpager适配器
        vPager.setOnPageChangeListener(new MyOnPageChangeListener());   //设置页面切换监听
        vPager.setPagingEnabled(true);   //false禁止左右滑动,true允许左右滑动
        vPager.setOffscreenPageLimit(2);   //设置缓存页面最大数
        //设置标签栏监听，根据用户点击显示相应的Fragment
        img_control.setOnClickListener(new MyOnClickListener(0));
        img_parameter.setOnClickListener(new MyOnClickListener(1));
        img_elecinfo.setOnClickListener(new MyOnClickListener(2));
        tx_control.setOnClickListener(new MyOnClickListener(0));
        tx_parameter.setOnClickListener(new MyOnClickListener(1));
        tx_elecinfo.setOnClickListener(new MyOnClickListener(2));

        tx_out.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                if(flag_room.equals("里间"))    //如果现在显示的是里间，则切换到外间，并通知正在显示的fragment
                {
                    tx_in.setTextColor(getResources().getColor(R.color.white));
                    tx_in.setBackgroundColor(getResources().getColor(R.color.bg_action));
                    tx_out.setTextColor(getResources().getColor(R.color.bg_action));
                    tx_out.setBackgroundColor(getResources().getColor(R.color.white));
                    flag_room = "外间";

                        Message msg = new Message();   //新建Message，用于向handler传递数据
                        msg.arg1 = 1;    //通知Fragment房间发生了变化，让其更新页面显示
                        control.handler1.sendMessage(msg);

                        Message msg1 = new Message();   //新建Message，用于向handler传递数据
                        msg1.arg1 = 1;    //通知Fragment房间发生了变化，让其更新页面显示
                        parameter.handler1.sendMessage(msg1);

                }
            }
        });
        tx_in.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(flag_room=="外间")
                {
                    tx_out.setTextColor(getResources().getColor(R.color.white));
                    tx_out.setBackgroundColor(getResources().getColor(R.color.bg_action));
                    tx_in.setTextColor(getResources().getColor(R.color.bg_action));
                    tx_in.setBackgroundColor(getResources().getColor(R.color.white));
                    flag_room = "里间";

                        Message msg = new Message();   //新建Message，用于向handler传递数据
                        msg.arg1 = 1;    //通知Fragment房间发生了变化，让其更新页面显示
                        control.handler1.sendMessage(msg);

                        Message msg1 = new Message();   //新建Message，用于向handler传递数据
                        msg1.arg1 = 1;    //通知Fragment房间发生了变化，让其更新页面显示
                        parameter.handler1.sendMessage(msg1);

                }
            }
        });

//    设置通知栏半透明
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            setTranslucentStatus(true);
        }

//        SystemBarTintManager tintManager = new SystemBarTintManager(this);
//        tintManager.setStatusBarTintEnabled(true);
//        tintManager.setStatusBarTintResource(R.color.bg_action);
    }

    @Override     //activity销毁时解除Service的绑定
    public void onDestroy()
    {
        unbindService(con);   //解除Service的绑定
        unregisterReceiver(broadcastreceiver);  //注销广播接收者
        super.onDestroy();
    }

    //viewpager适配器
    public class MyPagerAdapter extends FragmentPagerAdapter {     //viewpager适配器

        public MyPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        private final String[] titles = { "能源管控", "室内环境","用电信息"};
        //	private final String[] titles = { "能源管控"};
        @Override
        public CharSequence getPageTitle(int position) {
            return titles[position];    //获得与标题号对应的标题名
        }
        @Override
        public int getCount() {
            return titles.length;     //一共有几个头标
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:          //第一个fragment
                    if (control == null) {
                        control= new ControlFragment();
                        Bundle bundle = new Bundle();

                            /*	Bundle bundle = new Bundle();
								bundle.putString("clientip", clientip);   //传入IP地址
								control.setArguments(bundle);		*/
                    }
//                    if(room==null)
//                        room = new RoomFragment();
//                    layout_room.setVisibility(View.GONE);
//                    layout_title.setVisibility(View.VISIBLE);
                    return control;
                case 1:              //第二个fragment
                    if (parameter == null) {
                        parameter = new ParameterFragment();
                    }
						/*	Bundle bundle = new Bundle();
							bundle.putSerializable("data1", data1);
							bundle.putSerializable("data2", data2);
							hourFragment.setArguments(bundle);
							*/
//                    layout_room.setVisibility(View.VISIBLE);
//                    layout_title.setVisibility(View.GONE);
                    return parameter;
                case 2:   //第3个fragment
                    if(elecinfo==null)
                    {
                        elecinfo=new ElecinfoFragment();
                    }
                    return elecinfo;
                default:
                    return null;
            }
        }
        @Override
        public int getItemPosition(Object object) {
            return POSITION_NONE;
        }

    }
    /**
     * 头标点击监听
     */
    public class MyOnClickListener implements View.OnClickListener {
        private int index = 0;

        public MyOnClickListener(int i) {
            index = i;
        }

        @Override
        public void onClick(View v) {
            switch(v.getId())   //判断按下的按钮id，设置标题两个textview的背景颜色
            {
//                case R.id.tx_out:
//                {
//                    tx_out.setTextColor(getResources().getColor(R.color.white));
//                    tx_out.setBackgroundColor(getResources().getColor(R.color.bg_action));
//                    tx_in.setTextColor(getResources().getColor(R.color.bg_action));
//                    tx_in.setBackgroundColor(getResources().getColor(R.color.white));
//                    flag_room="外间";
//
//                }
//                case R.id.tx_in:
//                {
//                    tx_in.setTextColor(getResources().getColor(R.color.white));
//                    tx_in.setBackgroundColor(getResources().getColor(R.color.bg_action));
//                    tx_out.setTextColor(getResources().getColor(R.color.bg_action));
//                    tx_out.setBackgroundColor(getResources().getColor(R.color.white));
//                    flag_room="里间";
//                }
                case R.id.tx_control:
                case R.id.img_control:
                {
                    tx_control.setTextColor(Color.parseColor("#EB0000"));  //红色
                    img_control.clearColorFilter();    //先清除之前的滤镜效果
                    img_control.setColorFilter(0xffEB0000);                //红色
                    tx_parameter.setTextColor(Color.GRAY);     //灰色
                    img_parameter.clearColorFilter();   //清除之前的滤镜效果
                    img_parameter.setColorFilter(Color.GRAY);  //灰色
                    tx_elecinfo.setTextColor(Color.GRAY);     //灰色
                    img_elecinfo.clearColorFilter();   //清除之前的滤镜效果
                    img_elecinfo.setColorFilter(Color.GRAY);  //灰色

                    layout_room.setVisibility(View.GONE);
                    layout_title.setVisibility(View.VISIBLE);
                    break;
                }
                case R.id.tx_parameter:
                case R.id.img_parameter:
                {
                    tx_control.setTextColor(Color.GRAY);     //灰色
                    img_control.clearColorFilter();    //先清除之前的滤镜效果
                    img_control.setColorFilter(Color.GRAY);  //灰色
                    tx_parameter.setTextColor(Color.parseColor("#EB0000"));  //红色
                    img_parameter.clearColorFilter();   //清除之前的滤镜效果
                    img_parameter.setColorFilter(0xffEB0000);    //红色
                    tx_elecinfo.setTextColor(Color.GRAY);     //灰色
                    img_elecinfo.clearColorFilter();   //清除之前的滤镜效果
                    img_elecinfo.setColorFilter(Color.GRAY);  //灰色

                    layout_room.setVisibility(View.VISIBLE);
                    layout_title.setVisibility(View.GONE);
                    break;
                }
                case R.id.tx_elecinfo:
                case R.id.img_elecinfo:
                {
                    tx_control.setTextColor(Color.GRAY);     //灰色
                    img_control.clearColorFilter();    //先清除之前的滤镜效果
                    img_control.setColorFilter(Color.GRAY);  //灰色
                    tx_parameter.setTextColor(Color.GRAY);  //灰色
                    img_parameter.clearColorFilter();   //清除之前的滤镜效果
                    img_parameter.setColorFilter(Color.GRAY);    //灰色
                    tx_elecinfo.setTextColor(Color.parseColor("#EB0000"));     //红色
                    img_elecinfo.clearColorFilter();   //清除之前的滤镜效果
                    img_elecinfo.setColorFilter(0xffEB0000);  //红色

                    layout_room.setVisibility(View.GONE);
                    layout_title.setVisibility(View.VISIBLE);
                    break;
                }
                default:break;
            }
            vPager.setCurrentItem(index);   //根据头标选择的内容  对viewpager进行页面切换
        }
    };
    /**
     * 页卡切换监听
     */
    public class MyOnPageChangeListener implements OnPageChangeListener {
        @Override
        public void onPageSelected(int arg0) {
            switch(arg0)   //根据页面滑到哪一页，设置标题两个textview的背景颜色
            {
                case 0:
                {
                    tx_control.setTextColor(Color.parseColor("#EB0000"));  //红色
                    img_control.clearColorFilter();    //先清除之前的滤镜效果
                    img_control.setColorFilter(0xffEB0000);                //红色
                    tx_parameter.setTextColor(Color.GRAY);     //灰色
                    img_parameter.clearColorFilter();   //清除之前的滤镜效果
                    img_parameter.setColorFilter(Color.GRAY);  //灰色
                    tx_elecinfo.setTextColor(Color.GRAY);     //灰色
                    img_elecinfo.clearColorFilter();   //清除之前的滤镜效果
                    img_elecinfo.setColorFilter(Color.GRAY);  //灰色

                    layout_room.setVisibility(View.GONE);
                    layout_title.setVisibility(View.VISIBLE);
                    break;
                }
                case 1:
                {
                    tx_control.setTextColor(Color.GRAY);     //灰色
                    img_control.clearColorFilter();    //先清除之前的滤镜效果
                    img_control.setColorFilter(Color.GRAY);  //灰色
                    tx_parameter.setTextColor(Color.parseColor("#EB0000"));  //红色
                    img_parameter.clearColorFilter();   //清除之前的滤镜效果
                    img_parameter.setColorFilter(0xffEB0000);    //红色
                    tx_elecinfo.setTextColor(Color.GRAY);     //灰色
                    img_elecinfo.clearColorFilter();   //清除之前的滤镜效果
                    img_elecinfo.setColorFilter(Color.GRAY);  //灰色


                    layout_room.setVisibility(View.GONE);
                    layout_title.setVisibility(View.VISIBLE);
                    break;
                }
                case 2:
                {
                    tx_control.setTextColor(Color.GRAY);     //灰色
                    img_control.clearColorFilter();    //先清除之前的滤镜效果
                    img_control.setColorFilter(Color.GRAY);  //灰色
                    tx_parameter.setTextColor(Color.GRAY);     //灰色
                    img_parameter.clearColorFilter();   //清除之前的滤镜效果
                    img_parameter.setColorFilter(Color.GRAY);  //灰色
                    tx_elecinfo.setTextColor(Color.parseColor("#EB0000"));  //红色
                    img_elecinfo.clearColorFilter();   //清除之前的滤镜效果
                    img_elecinfo.setColorFilter(0xffEB0000);                //红色

                    layout_room.setVisibility(View.GONE);
                    layout_title.setVisibility(View.VISIBLE);
                    break;
                }

                default:break;
            }
        }
        @Override
        public void onPageScrolled(int arg0, float arg1, int arg2) {
        }

        @Override
        public void onPageScrollStateChanged(int arg0) {
        }
    }

    /**
     * *
     * 显示菜单栏，如果原来就是显示的，则将其关闭
     */
    private void Showpopwindow() {
        //        Log.i("Pop", "结果：" + ((mainpopview==null)?"null":mainpopview.toString()));
        if (mainpopwindow == null) {
            LayoutInflater layoutInflater = (LayoutInflater) getSystemService(this.LAYOUT_INFLATER_SERVICE);
            View view = layoutInflater.inflate(R.layout.mainpopview, null);
            LinearLayout voice = (LinearLayout) view.findViewById(R.id.voice);         //语音配置
            LinearLayout config = (LinearLayout) view.findViewById(R.id.config);         // 服务器端口配置
            LinearLayout warning = (LinearLayout) view.findViewById(R.id.warning);           //报警配置
            LinearLayout infraredlearn=(LinearLayout)view.findViewById(R.id.infraredlearn);   //红外学习
            LinearLayout logout = (LinearLayout) view.findViewById(R.id.logout);        //退出程序
           // LinearLayout backto = (LinearLayout) view.findViewById(R.id.backto);    //恢复出厂
            voice.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {    //语音配置
                    mainpopwindow.dismiss();
                    Intent intent = new Intent();
                    intent.setClass(Main_Activity.this, VoiceConfig_Activity.class);//设置要跳转的activity
                    Main_Activity.this.startActivity(intent);//开始跳转
                }
            });
            config.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {     //端口配置
                    mainpopwindow.dismiss();
                    Intent intent = new Intent();
                    intent.setClass(Main_Activity.this, ServerConfig_Activity.class);//设置要跳转的activity
                    Main_Activity.this.startActivity(intent);//开始跳转
                }
            });
            logout.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {    //退出程序
                    mainpopwindow.dismiss();
//                    Intent intent = new Intent();
//                    intent.setClass(Main_Activity.this, LogIn_Activity.class);//设置要跳转的activity
//                    Main_Activity.this.startActivity(intent);//开始跳转
                    finish();
                }
            });
            warning.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {    //报警配置页面
                    mainpopwindow.dismiss();
                    Intent intent = new Intent();
                    intent.setClass(Main_Activity.this, WarningConfig_Activity.class);//设置要跳转的activity
                    Main_Activity.this.startActivity(intent);//开始跳转
                }
            });

            infraredlearn.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent();
                    intent.setClass(Main_Activity.this, InfraredLearn_Activity.class);
                    startActivity(intent);    //红外学习页面

                }
            });
//            backto.setOnClickListener(new OnClickListener() {
//                @Override
//                public void onClick(View v) {    //恢复出厂配置页面
//                    mainpopwindow.dismiss();
//                }
//            });

            view.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {   //此处是为了监听menu键
                    if (keyCode == KeyEvent.KEYCODE_MENU) {
                        Log.i("Button", "弹出框监听到" + String.valueOf(keyCode));
                        if (Flag == 0)
                            mainpopwindow.dismiss();
                        Flag = 0;
                        return true;
                    }
                    return true;
                }
            });

            mainpopwindow = new PopupWindow(view,
                    Converts.dip2px(this, 220), ViewGroup.LayoutParams.WRAP_CONTENT, true);
            mainpopwindow.setTouchable(true);
            mainpopwindow.setFocusable(true);
            view.setFocusableInTouchMode(true);
            view.setFocusable(true);
            mainpopwindow.setTouchInterceptor(new View.OnTouchListener() {

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return false;
                    // 这里如果返回true的话，touch事件将被拦截
                    // 拦截后 PopupWindow的onTouchEvent不被调用，这样点击外部区域无法dismiss
                }
            });

            // 如果不设置PopupWindow的背景，无论是点击外部区域还是Back键都无法dismiss弹框
            // 我觉得这里是API的一个bug
            mainpopwindow.setBackgroundDrawable(getResources().getDrawable(
                    R.drawable.pop_shape));
            //    setPopupWindowTouchModal(mainpopwindow,false);   //设置不关闭popwindow也能相应事件
        }
        if (mainpopwindow.isShowing()) {
            mainpopwindow.dismiss();
            Log.i("Button", "弹出框已经销毁");
        } else {
            mainpopwindow.showAsDropDown(setting, 0, 0);  // Converts.dip2px(this, 2)
            Log.i("Button", "弹出框已经弹出");
        }
    }

    //连按两次返回键退出
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {    //返回键

            if ((System.currentTimeMillis() - mExitTime) > 2000) {// 如果两次按键时间间隔大于2000毫秒，则不退出
                Toast.makeText(this, "再按一次返回键退出", Toast.LENGTH_SHORT).show();
                mExitTime = System.currentTimeMillis();// 更新mExitTime
            } else {
                finish();
                //System.exit(0);// 连续点击两次返回键，退出程序

            }
            return true;
        }
        else if (keyCode == KeyEvent.KEYCODE_MENU)    //menu键，弹出菜单
        {

            Flag = 1;
            Showpopwindow();

            return true;


        }
        return super.onKeyDown(keyCode, event);
    }
    @TargetApi(19)   //屏幕状态栏进行透明化处理
    private void setTranslucentStatus(boolean on) {
        Window win = getWindow();
        WindowManager.LayoutParams winParams = win.getAttributes();
        final int bits = WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;
        if (on) {
            winParams.flags |= bits;
        } else {
            winParams.flags &= ~bits;
        }
        win.setAttributes(winParams);
    }

}
