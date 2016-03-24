package views;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.suntrans.zhongshui.Address;
import com.suntrans.zhongshui.Main_Activity;
import com.suntrans.zhongshui.R;
import com.suntrans.zhongshui.SwitchState;

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
import me.isming.sticker.library.StickerView;

/**
 * Created by 1111b on 2015/12/29.
 */
public class RoomFragment extends Fragment{
    private int result_code=0;
    private int ImgWidth;   //开关图标宽度
    private FrameLayout layout;   //整体的layout
    private byte[] bits={(byte)0x01,(byte)0x02,(byte)0x04,(byte)0x08,(byte)0x10,(byte)0x20,(byte)0x40,(byte)0x80};     //从1到8只有一位是1，用于按位与计算，获取某一位的值
    private ArrayList<StickerView> stickerView=new ArrayList<>();    //存放外间所有标签图片的数组
    private ArrayList<StickerView> stickerView1=new ArrayList<>();   //存放里间所有标签图片的数组
    private ArrayList<Map<String,Object>> data=new ArrayList<Map<String, Object>>();   //外间的数据
    private ArrayList<Map<String,Object>> data1=new ArrayList<Map<String, Object>>();    //里间的数据
    private String addr=Address.addr_out;   //开关的地址
    private Bitmap bmap;   //外间的背景图片
    private Bitmap bmap1;   //里间的背景图片
  //  private Bitmap bitmap;  //图标
    private ImageView image;   //背景图片
    private int Mainstate=0;   //外间总开关的状态，为0表示关
    private int Mainstate1=0;   //里间总开关的状态，为0表示关
    private ProgressDialog progressdialog;    //圆形加载条
    private long time;
   // private int i=0;
    private String which="100";   //which=100，表示没有命令在操作
    public Handler handler1=new Handler(){
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.arg1 == 1) {    //房间进行了切换，刷新List的显示

                if(((Main_Activity)getActivity()).flag_room.equals("外间"))
                    addr= Address.addr_out;
                else
                    addr=Address.addr_in;
               ViewInit();
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
                    if (s.substring(0, 8).equals("ab68"+Address.addr_out))
                        IsEffective = 1;    //外间
                    else if(s.substring(0, 8).equals("ab68"+Address.addr_in))
                        IsEffective=2;   //里间
                }
                if(IsEffective==1)   //外间的开关数据
                {
                    if (s.substring(10, 12).equals("03"))   //如果是读寄存器状态，解析出开关状态
                    {
                        if (s.substring(12, 14).equals("0e")||s.substring(12,14).equals("07"))
                        {
                            String[] states={"0","0","0","0","0","0","0","0","0","0"};   //十个通道的状态，state[0]对应1通道
                            for(int i=0;i<8;i++)   //先获取前八位的开关状态
                            {
                                states[i]=((a[8]&bits[i])==bits[i])?"1":"0";   //1-8通道
                            }
                            for(int i=0;i<2;i++)
                            {
                                states[i+8]=((a[7]&bits[i])==bits[i])?"1":"0";  //9、10通道
                            }
                            Mainstate=((a[7]&bits[2])==bits[2])?1:0;
                            for(int i=0;i<10;i++)
                                data.get(i).put("State", states[Integer.valueOf(data.get(i).get("Channel").toString().equals("a")?"10":data.get(i).get("Channel").toString())-1]);
                            ViewInit();

                        }
                    }
                    else if(s.substring(10,12).equals("06"))   //单个通道状态发生改变
                    {
                        int k=0;         //k是通道号
                        int state=Integer.valueOf(s.substring(19, 20));  //开关状态，1代表打开，0代表关闭
                        if(s.substring(15,16).equals("a"))
                            k=10;
                        else
                            k=Integer.valueOf(s.substring(15, 16));   //通道号,int型
                        if(k==0)                                          //如果通道号为0，则是总开关
                        {
                            Mainstate=state; //更新总开关数组中的开关状态
                            if(state==0)   //如果总开关关了，那肯定所有通道都关了
                            {
                                for(int i=0;i<data.size();i++)
                                    data.get(i).put("State", "0");
                            }
                        }
                        else     //如果通道号不为0，则更改data中的状态，并更新
                        {
                            for(int i=0;i<data.size();i++)
                            {
                                if(data.get(i).get("Channel").equals(String.valueOf(k)))
                                    data.get(i).put("State", state==1?"1":"0");
                            }
                        }
                        if(String.valueOf(k).equals(which)&&((Main_Activity)getActivity()).flag_room.equals("外间"))
                        {
                            which="100";
                            Message message = new Message();
                            message.what =0;       //0表示要隐藏
                            handler2.sendMessage(message);
                        }
                        ViewInit();
                    }
                }

                else if(IsEffective==2)   //里间的开关数据
                {
                    if (s.substring(10, 12).equals("03"))   //如果是读寄存器状态，解析出开关状态
                    {
                        if (s.substring(12, 14).equals("0e")||s.substring(12,14).equals("07"))
                        {
                            String[] states={"0","0","0","0","0","0","0","0","0","0"};   //十个通道的状态，state[0]对应1通道
                            for(int i=0;i<8;i++)   //先获取前八位的开关状态
                            {
                                states[i]=((a[8]&bits[i])==bits[i])?"1":"0";   //1-8通道
                            }
                            for(int i=0;i<2;i++)
                            {
                                states[i+8]=((a[7]&bits[i])==bits[i])?"1":"0";  //9、10通道
                            }
                            Mainstate1=((a[7]&bits[2])==bits[2])?1:0;
                            for(int i=0;i<data1.size();i++)
                                data1.get(i).put("State", states[Integer.valueOf(data1.get(i).get("Channel").toString().equals("a")?"10":data1.get(i).get("Channel").toString())-1]);
                            ViewInit();
                        }
                    }
                    else if(s.substring(10,12).equals("06"))   //单个通道状态发生改变
                    {
                        int k=0;         //k是通道号
                        int state=Integer.valueOf(s.substring(19, 20));  //开关状态，1代表打开，0代表关闭
                        if(s.substring(15,16).equals("a"))
                            k=10;
                        else
                            k=Integer.valueOf(s.substring(15, 16));   //通道号,int型
                        if(k==0)                                          //如果通道号为0，则是总开关
                        {
                            Mainstate1=state; //更新总开关数组中的开关状态
                            if(state==0)   //如果总开关关了，那肯定所有通道都关了
                            {
                                for(int i=0;i<data1.size();i++)
                                    data1.get(i).put("State", "0");
                            }
                        }
                        else     //如果通道号不为0，则更改data中的状态，并更新
                        {
                            for(int i=0;i<data1.size();i++)
                            {
                                if(data1.get(i).get("Channel").equals(String.valueOf(k)))
                                    data1.get(i).put("State", state==1?"1":"0");
                            }
                        }
                        if(String.valueOf(k).equals(which)&&((Main_Activity)getActivity()).flag_room.equals("里间"))
                        {
                            which="100";
                            Message message = new Message();
                            message.what =0;       //0表示要隐藏
                            handler2.sendMessage(message);
                        }
                        ViewInit();
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
                progressdialog = new ProgressDialog(getActivity());    //初始化progressdialog
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
                progressdialog = new ProgressDialog(getActivity());    //初始化progressdialog
                progressdialog.setCancelable(true);// 设置是否可以通过点击Back键取消
                progressdialog.setCanceledOnTouchOutside(false);// 设置在点击Dialog外是否取消Dialog进度条
                progressdialog.show();
                progressdialog.setContentView(R.layout.progressdialog);    //设置显示的内容
            }
        }
    };
    @Override
    public void onResume(){
        super.onResume();
        DataInit();   //数据初始化
        ViewInit();    //视图初始化
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
       // Log.i("Order", "control==>onCreateView");
        View view = inflater.inflate(R.layout.room, null);
       // view.setOnTouchListener(this);   //为此页面设置自定义的触摸监听

        layout = (FrameLayout) view.findViewById(R.id.layout);
        image = (ImageView) view.findViewById(R.id.image);
        //bitmap = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.bulb1);
//        DataInit();  //数据初始化
//        ViewInit();  //视图初始化
        return view;
    }

    /****
     * 数据初始化。提取数据库中两个房间开关通道的相关信息，分别存入data和data1中
     */
    private void DataInit()
    {
        DbHelper dh1 = new DbHelper(getActivity(), "IBMS", null, 1);
        SQLiteDatabase db = dh1.getWritableDatabase();
        db.beginTransaction();
        data = new ArrayList<Map<String,Object>>();
        data1=new ArrayList<Map<String,Object>>();
        //外间的数据
        Cursor cursor = db.query("switchs_tb", new String[]{"CID", "Name", "Image", "IsShow", "Matrix", "Channel"}, "Room=?", new String[]{"外间"}, null, null, null, null);
        while (cursor.moveToNext()) {
            String IsShow = cursor.getString(3);   //是否需要显示，“1”代表需要显示，0代表不需要显示
            byte[] in = cursor.getBlob(2);     //获取图片
            String Name = cursor.getString(1);    //通道名称
            String Cid = cursor.getString(0);    //通道ID
            String matrix = cursor.getString(4);    //图片的参数矩阵
            String Channel = cursor.getString(5);   //通道号
            Bitmap bitmap = BitmapFactory.decodeByteArray(in, 0, in.length);   //转化成bitmap格式
            Map<String, Object> map=new HashMap<String,Object>();
            map.put("IsShow", IsShow);
            map.put("CID", Cid);
            map.put("Name", Name);
            map.put("Matrix", matrix);
            map.put("Image", bitmap);
            map.put("State", "false");   //开关状态
            map.put("Channel", Channel);
            data.add(map);
        }

        //里间的数据
        cursor = db.query("switchs_tb", new String[]{"CID", "Name", "Image", "IsShow", "Matrix", "Channel"}, "Room=?", new String[]{"里间"}, null, null, null, null);
        while (cursor.moveToNext()) {
            String IsShow = cursor.getString(3);   //是否需要显示，“1”代表需要显示，0代表不需要显示
            byte[] in = cursor.getBlob(2);     //获取图片
            String Name = cursor.getString(1);    //通道名称
            String Cid = cursor.getString(0);    //通道ID
            String matrix = cursor.getString(4);    //图片的参数矩阵
            String Channel = cursor.getString(5);   //通道号
            Bitmap bitmap = BitmapFactory.decodeByteArray(in, 0, in.length);   //转化成bitmap格式
            Map<String, Object> map=new HashMap<String,Object>();
            map.put("IsShow", IsShow);
            map.put("CID", Cid);
            map.put("Name", Name);
            map.put("Matrix", matrix);
            map.put("Image", bitmap);
            map.put("State", "false");   //开关状态
            map.put("Channel", Channel);
            data1.add(map);

        }

        //外间房间背景图片
        cursor = db.query("room_tb", new String[]{"RID", "Name", "Image"}, "Name=?", new String[]{"外间"}, null, null, null, null);
        while (cursor.moveToNext()) {
            byte[] in = cursor.getBlob(2);
            bmap = BitmapFactory.decodeByteArray(in, 0, in.length);
        }
        //里间房间背景图片
        cursor = db.query("room_tb", new String[]{"RID", "Name", "Image"}, "Name=?", new String[]{"里间"}, null, null, null, null);
        while (cursor.moveToNext()) {
            byte[] in = cursor.getBlob(2);
            bmap1 = BitmapFactory.decodeByteArray(in, 0, in.length);
        }
        db.setTransactionSuccessful();
        db.endTransaction();
        db.close();
    }

    /***
     * 视图初始化，初始化背景图片，通道标签图片等
     */
    private void ViewInit() {

        if (((Main_Activity) getActivity()).flag_room.equals("外间"))    //如果正在显示的是外间
        {
            stickerView = new ArrayList<>();   //初始化标签数组
            for (int i = 0; i < 10; i++) {
                final int position=i;
                Map<String, Object> map = new HashMap<>(data.get(i));
                final String IsShow = map.get("IsShow").toString();    //是否显示
                final String channel = map.get("Channel").toString();   //通道号
                final String state = new String((SwitchState.getSate(Address.addr_out))[Integer.valueOf(channel)]);    //获取开关状态
                final String matrix = map.get("Matrix").toString();  //图标的矩阵参数
                Bitmap bitmap=(Bitmap)map.get("Image");
                if (IsShow.equals("1"))   //如果确认要显示
                {
                    Matrix matrix1 = null;
                    if (!matrix.equals("null")) {
                        String[] s = matrix.split(";");
                        float[] values = new float[9];
                        for (int k = 0; k < 9; k++)
                            values[k] = Float.valueOf(s[k]);
                        matrix1 = new Matrix();
                        matrix1.setValues(values);
                    }
                    StickerView sticker1 = new StickerView(getActivity());
                    sticker1.setEditable(false);
                    sticker1.setShowDrawController(false);
                    sticker1.setTab(Integer.valueOf(channel));   //设置对应的通道号
                    sticker1.setState(state.equals("1") ? true : false);
                    sticker1.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                          //  Toast.makeText(getActivity().getApplication(),SwitchState.getSate(Address.addr_out)[Integer.valueOf(channel)],Toast.LENGTH_SHORT).show();
                            if(which.equals("100")) {
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

//
                                if(state.equals("0") &&(Mainstate == 0&&addr.equals(Address.addr_out)||(Mainstate1==0&&addr.equals(Address.addr_in)))) //如果是要打开开关，并且总开关没有打开，则先打开总开关
                                {
                                    new Thread() {
                                        public void run() {
                                            ((Main_Activity) getActivity()).binder.sendOrder(addr+" 0106 0300 0001",2);
                                            try {
                                                Thread.sleep(50);
                                            } catch (Exception e) {
                                            }
                                            ((Main_Activity) getActivity()).binder.sendOrder(addr+" 0106 030" + (channel.equals("10") ? "a" : channel) + " 000" + (state.equals("0") ? "1" : "0"),2);
                                        }
                                    }.start();
                                }
                                else   //关闭该通道
                                    ((Main_Activity) getActivity()).binder.sendOrder(addr + "0106 030" + (channel.equals("10") ? "a" : channel) + " 000" + (state.equals("0") ? "1" : "0"),2);

                            }
                        }
                    });
                    sticker1.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                            builder.setTitle("编辑开关信息：");
                            builder.setItems(new String[]{"更换图标"}, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    //点击后弹出窗口选择了第几项
                                    //Toast.makeText(getApplicationContext(),"你点击了第"+which+"个图标",Toast.LENGTH_SHORT).show();
                                    switch(which)
                                    {
//                                        case 0:  //修改名称
//                                        {
//                                            LayoutInflater factory = LayoutInflater.from(getActivity());
//                                            final View view = factory.inflate(R.layout.hoursedialog, null);
//                                            final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
//                                            builder.setTitle("请输入通道名称：");
//                                            final EditText tx1= (EditText) view.findViewById(R.id.tx1);
//                                            tx1.setText(name);
//                                            tx1.setSelection(name.length());
//                                            builder.setView(view);
//                                            builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
//                                                public void onClick(DialogInterface dialog, int whichButton) {
//
//                                                    String New_Name=tx1.getText().toString();
//                                                    DbHelper dh1=new DbHelper(getActivity(),"IBMS",null,1);
//                                                    SQLiteDatabase db = dh1.getWritableDatabase();
//                                                    ContentValues cv = new ContentValues();    //内容数组
//                                                    //  Cursor cursor = db.query(true, "switchs_tb", new String[]{"Room","Name","Channel","Area","CID"}, "Area=?", new String[]{area}, null, null, null, null);
//                                                    cv.put("Name", New_Name);
//                                                    db.update("switchs_tb", cv, "CID=?", new String[]{CID});
//                                                    //   Cursor cursor = db.query(true, "room_tb", new String[]{"RID","Name"}, null, null, null, null, null, null, null);
//                                                    data=new ArrayList<Map<String, Object>>();
//                                                    data1 = new ArrayList<Map<String, Object>>();
//                                                    DataInit();
//
//                                                    Adapter.this.notifyDataSetChanged();   //刷新
//                                                    Toast.makeText(getActivity().getApplicationContext(),"修改成功！",Toast.LENGTH_SHORT).show();
//
//
//                                                }
//                                            });
//                                            builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
//                                                public void onClick(DialogInterface dialog, int whichButton) {
//
//                                                }
//                                            });
//                                            builder.create().show();
//
//                                            break;
//                                        }
                                        case 0:  //  更换图标
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
                                                            Intent intent = new Intent(
                                                                    Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                                                            startActivityForResult(intent,10000); //请求码为10000，  用来区分图片是裁剪前的还是裁剪后的
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

                                        default:break;
                                    }
                                }
                            });
                            builder.show();
                            //Toast.makeText(getActivity().getApplicationContext(), "触发了长点击事件！", Toast.LENGTH_SHORT).show();
                            return true;
                        }
                    });
                    if (matrix1 != null)
                        sticker1.setMarkMatrix(matrix1);
                    sticker1.setWaterMark(bitmap);
                    stickerView.add(sticker1);

                }
            }
            layout.removeAllViews();
            image.setImageBitmap(bmap);
            layout.addView(image);
            for (StickerView sv : stickerView) {
                sv.setShowDrawController(false);
                sv.setEditable(false);
                layout.addView(sv);
            }
        } else   //如果正在显示的是里间
        {
            stickerView1 = new ArrayList<>();   //初始化标签数组
            for (int i = 0; i < 10; i++) {
                final int position=i;
                Map<String, Object> map = new HashMap<>(data1.get(i));
                final String IsShow = map.get("IsShow").toString();
                final String channel = map.get("Channel").toString();
                final String state = SwitchState.getSate(Address.addr_in)[Integer.valueOf(channel)];    //获取开关状态
                final String matrix = map.get("Matrix").toString();  //图标的矩阵参数
                Bitmap bitmap=(Bitmap)map.get("Image");
                if (IsShow.equals("1"))   //如果确认要显示
                {
                    Matrix matrix1 = null;
                    if (!matrix.equals("null")) {
                        String[] s = matrix.split(";");
                        float[] values = new float[9];
                        for (int k = 0; k < 9; k++)
                            values[k] = Float.valueOf(s[k]);
                        matrix1 = new Matrix();
                        matrix1.setValues(values);
                    }
                    StickerView sticker1 = new StickerView(getActivity());
                    sticker1.setEditable(false);
                    sticker1.setShowDrawController(false);
                    sticker1.setTab(Integer.valueOf(channel));
                    sticker1.setState(state.equals("1") ? true : false);
                    sticker1.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (which.equals("100")) {
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


                                if (state.equals("0") &&
                                        (Mainstate == 0 && addr.equals(Address.addr_out) || (Mainstate1 == 0 && addr.equals(Address.addr_in)))) //如果是要打开开关，并且总开关没有打开，则先打开总开关
                                {
                                    new Thread() {
                                        public void run() {
                                            ((Main_Activity) getActivity()).binder.sendOrder(addr + " 0106 0300 0001",2);
                                            try {
                                                Thread.sleep(50);
                                            } catch (Exception e) {
                                            }
                                            ((Main_Activity) getActivity()).binder.sendOrder(addr + " 0106 030" + (channel.equals("10") ? "a" : channel) + " 000" + (state.equals("0") ? "1" : "0"),2);
                                        }
                                    }.start();
                                } else
                                    ((Main_Activity) getActivity()).binder.sendOrder(addr + "0106 030" + (channel.equals("10") ? "a" : channel) + " 000" + (state.equals("0") ? "1" : "0"),2);

                            }
                            //Toast.makeText(getActivity().getApplicationContext(), "点击了标签", Toast.LENGTH_SHORT).show();
                        }
                    });
                    sticker1.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                            builder.setTitle("编辑开关信息：");
                            builder.setItems(new String[]{"更换图标"}, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    //点击后弹出窗口选择了第几项
                                    //Toast.makeText(getApplicationContext(),"你点击了第"+which+"个图标",Toast.LENGTH_SHORT).show();
                                    switch (which) {
//                                        case 0:  //修改名称
//                                        {
//                                            LayoutInflater factory = LayoutInflater.from(getActivity());
//                                            final View view = factory.inflate(R.layout.hoursedialog, null);
//                                            final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
//                                            builder.setTitle("请输入通道名称：");
//                                            final EditText tx1= (EditText) view.findViewById(R.id.tx1);
//                                            tx1.setText(name);
//                                            tx1.setSelection(name.length());
//                                            builder.setView(view);
//                                            builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
//                                                public void onClick(DialogInterface dialog, int whichButton) {
//
//                                                    String New_Name=tx1.getText().toString();
//                                                    DbHelper dh1=new DbHelper(getActivity(),"IBMS",null,1);
//                                                    SQLiteDatabase db = dh1.getWritableDatabase();
//                                                    ContentValues cv = new ContentValues();    //内容数组
//                                                    //  Cursor cursor = db.query(true, "switchs_tb", new String[]{"Room","Name","Channel","Area","CID"}, "Area=?", new String[]{area}, null, null, null, null);
//                                                    cv.put("Name", New_Name);
//                                                    db.update("switchs_tb", cv, "CID=?", new String[]{CID});
//                                                    //   Cursor cursor = db.query(true, "room_tb", new String[]{"RID","Name"}, null, null, null, null, null, null, null);
//                                                    data=new ArrayList<Map<String, Object>>();
//                                                    data1 = new ArrayList<Map<String, Object>>();
//                                                    DataInit();
//
//                                                    Adapter.this.notifyDataSetChanged();   //刷新
//                                                    Toast.makeText(getActivity().getApplicationContext(),"修改成功！",Toast.LENGTH_SHORT).show();
//
//
//                                                }
//                                            });
//                                            builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
//                                                public void onClick(DialogInterface dialog, int whichButton) {
//
//                                                }
//                                            });
//                                            builder.create().show();
//
//                                            break;
//                                        }
                                        case 0:  //  更换图标
                                        {
                                            builder.setTitle("更换图标：");
                                            builder.setItems(new String[]{"本地图库", "拍照", "取消"}, new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int which) {
                                                    //点击后弹出窗口选择了第几项
                                                    //Toast.makeText(getApplicationContext(),"你点击了第"+which+"个图标",Toast.LENGTH_SHORT).show();
                                                    switch (which) {
                                                        case 0:    //选择本地图库
                                                        {
                                                            result_code = position;
                                                            //打开图库
                                                            Intent intent = new Intent(
                                                                    Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                                                            startActivityForResult(intent, 10000); //请求码为10000，  用来区分图片是裁剪前的还是裁剪后的
                                                            break;
                                                        }
                                                        case 1:    //选择拍照
                                                        {
                                                            result_code = position;
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
                                                        default:
                                                            break;
                                                    }
                                                }
                                            });
                                            builder.create().show();
                                            break;
                                        }

                                        default:
                                            break;
                                    }
                                }
                            });
                            builder.show();
                            //Toast.makeText(getActivity().getApplicationContext(), "触发了长点击事件！", Toast.LENGTH_SHORT).show();
                            return true;
                        }
                    });
                    if (matrix1 != null)
                        sticker1.setMarkMatrix(matrix1);
                    sticker1.setWaterMark(bitmap);
                    stickerView1.add(sticker1);

                }
            }
            layout.removeAllViews();
            image.setImageBitmap(bmap1);
            layout.addView(image);
            for (StickerView sv : stickerView1) {
                sv.setShowDrawController(false);
                sv.setEditable(false);
                layout.addView(sv);
            }
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data2) {
        super.onActivityResult(requestCode, resultCode, data2);
        if (resultCode != getActivity().RESULT_CANCELED)
        {
            if(requestCode==10000||requestCode==10001)//如果是刚刚选择完，还未裁剪，则跳转到裁剪的activity
            {
                if (data2 != null) {
                    //取得返回的Uri,基本上选择照片的时候返回的是以Uri形式，但是在拍照中有得机子呢Uri是空的，所以要特别注意
                    Uri mImageCaptureUri = data2.getData();
                    //返回的Uri不为空时，那么图片信息数据都会在Uri中获得。如果为空，那么我们就进行下面的方式获取
                    if (mImageCaptureUri != null) {
                        Bitmap image;
                        try {
                            //这个方法是根据Uri获取Bitmap图片的静态方法
                            image = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), mImageCaptureUri);
                            if (image != null) {
                                startPhotoZoom(mImageCaptureUri);    //打开裁剪activity
                            }
                        } catch (Exception e) {
                            Log.i("IBM","URI出错"+e.toString());
                        }
                    }
                    else {
                        Bundle extras = data2.getExtras();
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
                            Toast.makeText(getActivity().getApplicationContext(), "未找到存储卡，无法存储照片！", Toast.LENGTH_SHORT).show();
                        }
                    }

                }
            }
            else   //如果是裁剪后返回，调用的回调方法
            {
                if (data2 != null) {
                    //取得返回的Uri,基本上选择照片的时候返回的是以Uri形式，但是在拍照中有得机子呢Uri是空的，所以要特别注意
                    Uri mImageCaptureUri = data2.getData();
                    //返回的Uri不为空时，那么图片信息数据都会在Uri中获得。如果为空，那么我们就进行下面的方式获取
                    if (mImageCaptureUri != null) {
                        Log.i("IBM","URI不为空"+mImageCaptureUri.toString());
                        Bitmap image;
                        try {
                            //这个方法是根据Uri获取Bitmap图片的静态方法
                            image = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), mImageCaptureUri);
                            if (image != null) {
                                if(addr.equals(Address.addr_out))
                                    data.get(result_code).put("Image",image);
                                else
                                    data1.get(result_code).put("Image", image);
                                DbHelper dh1=new DbHelper(getActivity(),"IBMS",null,1);
                                SQLiteDatabase db = dh1.getWritableDatabase();
                                db.beginTransaction();
                                ContentValues cv = new ContentValues();    //内容数组
                                ByteArrayOutputStream os = new ByteArrayOutputStream();
                                image.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
                                cv.put("Image", os.toByteArray());
                                if(addr.equals(Address.addr_out))
                                    db.update("switchs_tb", cv, "CID=?", new String[]{data.get(result_code).get("CID").toString()});
                               else
                                    db.update("switchs_tb", cv, "CID=?", new String[]{data1.get(result_code).get("CID").toString()});
                                db.setTransactionSuccessful();
                                db.endTransaction();
                                db.close();
                               //更新页面显示
                                ViewInit();

                               // ((Adapter)grid.getAdapter()).notifyDataSetChanged();   //刷新
                            }
                        } catch (Exception e) {
                            Log.i("IBM","URI出错1"+e.toString()+"result__code:"+result_code);
                        }
                    }
                    else {
                        Bundle extras = data2.getExtras();
                        Log.i("IBM",extras.toString());
                        if (extras != null) {
                            //这里是有些拍照后的图片是直接存放到Bundle中的所以我们可以从这里面获取Bitmap图片
                            Bitmap image = extras.getParcelable("data");
                            if (image != null) {
                                if(addr.equals(Address.addr_out))
                                    data.get(result_code).put("Image",image);
                                else
                                    data1.get(result_code).put("Image", image);
                                DbHelper dh1=new DbHelper(getActivity(),"IBMS",null,1);
                                SQLiteDatabase db = dh1.getWritableDatabase();
                                db.beginTransaction();
                                ContentValues cv = new ContentValues();    //内容数组
                                ByteArrayOutputStream os = new ByteArrayOutputStream();
                                image.compress(Bitmap.CompressFormat.PNG, 100, os);    //os是输出流，存放图片
                                cv.put("Image", os.toByteArray());
                                if(addr.equals(Address.addr_out))
                                    db.update("switchs_tb", cv, "CID=?", new String[]{data.get(result_code).get("CID").toString()});
                               else
                                    db.update("switchs_tb", cv, "CID=?", new String[]{data1.get(result_code).get("CID").toString()});
                                db.setTransactionSuccessful();
                                db.endTransaction();
                                db.close();
                                //更新页面显示
                                ViewInit();
                                //((Adapter)grid.getAdapter()).notifyDataSetChanged();   //刷新
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
                Intent intent = new Intent(
                        Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent,10000); //请求码为10000，  用来区分图片是裁剪前的还是裁剪后的


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
        intent.putExtra("outputX", convert.Converts.dip2px(getActivity().getApplicationContext(), 130));
        intent.putExtra("outputY", convert.Converts.dip2px(getActivity().getApplicationContext(), 130));
        intent.putExtra("return-data", false);
        startActivityForResult(intent, result_code);
    }


}
