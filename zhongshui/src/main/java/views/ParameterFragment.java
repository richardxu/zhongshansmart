package views;

import java.io.IOException;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import services.MainService;

import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.handmark.pulltorefresh.library.PullToRefreshBase.Mode;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.suntrans.zhongshui.Address;
import com.suntrans.zhongshui.Main_Activity;
import com.suntrans.zhongshui.R;
import convert.Converts;

import android.app.AlertDialog;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Vibrator;
import android.support.v4.app.Fragment;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class ParameterFragment extends Fragment {
   // private int IsWarning=0;
    private int isVisible=0;   //本页面是否可见
    private PullToRefreshListView mPullRefreshListView;    //下拉列表控件
    private ListView list;   //列表
    private byte[] bits={(byte)0x01,(byte)0x02,(byte)0x04,(byte)0x08,(byte)0x10,(byte)0x20,(byte)0x40,(byte)0x80};     //从1到8只有一位是1，用于按位与计算，获取某一位的值
    private SeekBar seekbar_r,seekbar_g,seekbar_b;   //外间三个滚动条，红绿蓝
    private views.Switch switch_r,switch_g,switch_b;     //外间对应的三个开关，红绿蓝
    private int progress_r,progress_g,progress_b;      //外间三个滚动条的进度，0-255
    private int state_r,state_g,state_b;    //外间三个灯的开关状态，0表示关，1表示开

    private SeekBar seekbar_r1,seekbar_g1,seekbar_b1;   //里间三个滚动条，红绿蓝
    private views.Switch switch_r1,switch_g1,switch_b1;     //里间对应的三个开关，红绿蓝
    private int progress_r1,progress_g1,progress_b1;      //里间三个滚动条的进度，0-255
    private int state_r1,state_g1,state_b1;    //里间三个灯的开关状态，0表示关，1表示开
    private String clientip;    //保存开关的IP地址
    private Socket client;    //保持TCP连接的socket
    private DisplayMetrics displayMetrics = new DisplayMetrics();
    private int Pwidth=0;  //屏幕宽度，单位是pixel
    private String device="手机";   //是手机还是平板，根据屏幕宽度判断
    private String addr= Address.addr_out;   //外间地址，默认0004
    private ProgressDialog progressdialog;
    private ArrayList<Map<String, String>> data_room=new ArrayList<Map<String, String>>();    //室内环境    外间
    private ArrayList<Map<String, String>> data_air=new ArrayList<Map<String, String>>();    //空气质量     外间
    private ArrayList<Map<String, String>> data_posture = new ArrayList<>();   //姿态信息  外间
    private ArrayList<Map<String, String>> data_room1 = new ArrayList<Map<String,String>>();   ///室内环境  里间
    private ArrayList<Map<String, String>> data_air1=new ArrayList<Map<String, String>>();    //空气质量     里间
    private ArrayList<Map<String, String>> data_posture1 = new ArrayList<>();   //姿态信息  里间
    private String warning_state[]=new String[]{"1","1","1","1","1"};   //外间是否报警，依次是振动、PM2.5、甲醛、烟雾、温度
    private String warning_state1[]=new String[]{"1","1","1","1","1"};   //为1则报警，为0则不报警
    private MainService.ibinder binder=null;    //用于activity与service通讯的接口类
    private int x;
    private int y;   //记录list滚动到的位置
    private String which="100";    //用来标示是否有命令正在发送还没有返回，100表示没有正在发送的数据,2表示刷新所有参数，
    private long time;   //触发progressdialog显示的时间
    private SectionListAdapter adapter;
    public Handler handler1=new Handler()   //接收到TCP发回的数据后调用，用于更新列表中的开关状态，即反馈
    {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.arg1 == 1) {    //房间进行了切换，刷新List的显示
                ListInit();
                if(((Main_Activity)getActivity()).flag_room.equals("外间"))
                    addr=Address.addr_out;
                else
                    addr=Address.addr_in;
                if(isVisible==1)   //如果页面可见，则进行刷新
                    mPullRefreshListView.setRefreshing();
            }
            else   //收到新的数据，更新数据
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
             //   Log.i("Order", "收到数据：" + s);
                int IsEffective = 0;    //指令是否有效，0表示无效，1表示有效；对于和第六感官通讯而言，包头为ab68的数据才有效
                if (msg.what > 10) {
                    if (s.substring(0, 10).equals("ab68"+Address.addr_out+"f0"))
                        IsEffective = 1;    //外间
                    else if(s.substring(0, 10).equals("ab68"+Address.addr_in+"f0"))
                        IsEffective=2;   //里间
                }
                if (IsEffective == 1)   //如果数据有效，则进行解析，并更新页面，外间
                {
                    if (s.substring(10, 12).equals("04")||s.substring(10,12).equals("03"))   //如果是读寄存器状态，则判断是读寄存器1（室内参数），还是寄存器2（灯光信息）的状态
                    {
                        if (s.substring(12, 14).equals("22")&&a.length>40)  //寄存器1，参数信息，长度34个字节
                        {
                            //计算得到各个参数的值，顺序是按寄存器顺序来的
                            double tmp_old=((a[7] + 256) % 256) * 256 + (a[8] + 256) % 256;   //原始温度，即正常温度的100倍
                            if(tmp_old>30000)
                                tmp_old=tmp_old-65536;   //负温度值
                            double tmp = tmp_old/ 100.0;   //温度
                            double humidity = (((a[13] + 256) % 256) * 256 + (a[14] + 256) % 256) /10.0;   //湿度
                            while(humidity>100)
                                humidity=humidity/10.0;    //防止湿度出现大于100的数字
                            double atm = (((a[15] + 256) % 256) * 256 + (a[16] + 256) % 256) / 100.0;       //大气压
                            double arofene = (((a[17] + 256) % 256) * 256 + (a[18] + 256) % 256) / 1000.0;    //甲醛
                            double smoke = (((a[19] + 256) % 256) * 256 + (a[20] + 256) % 256);       //烟雾
                            double staff = (((a[21] + 256) % 256) * 256 + (a[22] + 256) % 256);     //人员信息
                            double light = (((a[23] + 256) % 256) * 256 + (a[24] + 256) % 256);  //光感
                            double pm1 = (((a[25] + 256) % 256) * 256 + (a[26] + 256) % 256);     //PM1
                            double pm25 = (((a[27] + 256) % 256) * 256 + (a[28] + 256) % 256);     //PM2.5
                            double pm10 = (((a[29] + 256) % 256) * 256 + (a[30] + 256) % 256);     //PM10
                            double xdegree= (((a[33] + 256) % 256) * 256 + (a[34] + 256) % 256)/100.0;   //X轴角度
                            double ydegree= (((a[35] + 256) % 256) * 256 + (a[36] + 256) % 256)/100.0;  //Y轴角度
                            double zdegree= (((a[37] + 256) % 256) * 256 + (a[38] + 256) % 256)/100.0;   //水平角度
                            double shake=(((a[39] + 256) % 256) * 256 + (a[40] + 256) % 256);   //振动强度

                            String switch_shake = (a[32] & bits[0]) == bits[0] ? "1" : "0";     //振动报警开关状态
                            String switch_pm25 = (a[32] & bits[1]) == bits[1] ? "1" : "0";        //PM2.5报警开关状态
                            String switch_arofene = (a[32] & bits[2]) == bits[2] ? "1" : "0";      //甲醛报警开关状态
                            String switch_smoke = (a[32] & bits[3]) == bits[3] ? "1" : "0";        //烟雾报警开关状态
                            String switch_tmp = (a[32] & bits[4]) == bits[4] ? "1" : "0";          //温度报警开关状态

                            data_room.get(0).put("Value", String.valueOf(tmp) + " ℃");  //温度值
                            data_room.get(1).put("Value", String.valueOf(humidity) + " %RH");  //湿度
                            data_room.get(2).put("Value", String.valueOf(atm) + " kPa");   //大气压
                            data_room.get(3).put("Value", String.valueOf(staff) + " ");     //人员信息
                            data_room.get(4).put("Value", String.valueOf(light) + " ");    //光感

                            data_air.get(0).put("Value", String.valueOf(smoke) + " ppm");    //烟雾
                            data_air.get(1).put("Value", String.valueOf(arofene) + " ppm");   //甲醛
                            data_air.get(2).put("Value", String.valueOf(pm1) + " ");     //PM1
                            data_air.get(3).put("Value", String.valueOf(pm25) + " ");   //PM2.5
                            data_air.get(4).put("Value", String.valueOf(pm10) + " ");   //PM10

                            data_posture.get(0).put("Value", String.valueOf(xdegree) + " °"); //x轴角度
                            data_posture.get(1).put("Value", String.valueOf(ydegree) + " °");  //y轴角度
                            if(zdegree>10)
                                data_posture.get(2).put("Value", String.valueOf(zdegree) + " °(倾斜)");   //水平角度
                            else
                                data_posture.get(2).put("Value", String.valueOf(zdegree) + " °(正常)");   //水平角度
                            data_posture.get(3).put("Value", String.valueOf(shake) );//振动强度

                            warning_state[0]=switch_shake;
                            warning_state[1]=switch_pm25;
                            warning_state[2]=switch_arofene;
                            warning_state[3]=switch_smoke;
                            warning_state[4]=switch_tmp;
                            //评估，空气质量部分
                            String eva = "null";//评估，优、良、轻度污染、中度污染、重度污染、严重污染
                            int progress = 0;//进度
                            if (smoke <= 750) {
                                eva = "清洁";
                                progress = (int) (smoke / 750 * 100 / 6);
                            } else {
                                eva = "污染";
                                progress = (int) (100 / 6 + (smoke - 750) * 500 / 9250 / 6);
                            }
                            data_air.get(0).put("Evaluate", eva);     //烟雾0-10000
                            data_air.get(0).put("Progress", String.valueOf(progress));

                            eva = "null";   //评估甲醛，0-1000
                            progress = 0;
                            if (arofene <= 0.1) {
                                eva = "清洁";
                                progress = (int) (arofene / 0.1 * 100 / 6);
                            } else {
                                eva = "超标";
                                progress = (int) (100 / 6 + (arofene - 0.1) * 500 / 6);
                                if (progress >= 80)
                                    progress = 80;
                            }
                            data_air.get(1).put("Evaluate", eva);     //甲醛，假设是0-1
                            data_air.get(1).put("Progress", String.valueOf(progress));

                            eva = "null";
                            progress = 0;
                            //评估,PM1
                            if (pm1 <= 35) {
                                eva = "优";
                                progress = (int) (pm1 / 35 / 6 * 100);
                            } else if (pm1 <= 75) {
                                eva = "良";
                                progress = (int) ((pm1 - 35) / 240 * 100 + 100 / 6);
                            } else if (pm1 <= 115) {
                                eva = "轻度污染";
                                progress = (int) ((pm1 - 75) / 240 * 100 + 200 / 6);
                            } else if (pm1 <= 150) {
                                eva = "中度污染";
                                progress = (int) ((pm1 - 115) / 35 / 6 * 100 + 300 / 6);
                            } else if (pm1 <= 250) {
                                eva = "重度污染";
                                progress = (int) ((pm1 - 150) / 6 + 400 / 6);
                            } else {
                                eva = "严重污染";
                                progress = 90;
                            }
                            data_air.get(2).put("Evaluate", eva);     //PM1
                            data_air.get(2).put("Progress", String.valueOf(progress));


                            eva = "null";  //评估,PM2.5
                            progress = 0;
                            if (pm25 <= 35) {
                                eva = "优";
                                progress = (int) (pm25 / 35 / 6 * 100);
                            } else if (pm25 <= 75) {
                                eva = "良";
                                progress = (int) ((pm25 - 35) / 240 * 100 + 100 / 6);
                            } else if (pm25 <= 115) {
                                eva = "轻度污染";
                                progress = (int) ((pm25 - 75) / 240 * 100 + 200 / 6);
                            } else if (pm25 <= 150) {
                                eva = "中度污染";
                                progress = (int) ((pm25 - 115) / 35 / 6 * 100 + 300 / 6);
                            } else if (pm25 <= 250) {
                                eva = "重度污染";
                                progress = (int) ((pm25 - 150) / 6 + 400 / 6);
                            } else {
                                eva = "严重污染";
                                progress = 90;
                            }
                            data_air.get(3).put("Evaluate", eva);     //PM2.5
                            data_air.get(3).put("Progress", String.valueOf(progress));
                            // Log.i("Order","计算得到："+String.valueOf(progress));

                            eva = "null";  //评估,PM10
                            progress = 0;
                            if (pm10 <= 50) {
                                eva = "优";
                                progress = (int) (pm10 / 50 * 100 / 6);
                            } else if (pm10 <= 150) {
                                eva = "良";
                                progress = (int) ((pm10 - 50) / 6 + 100 / 6);
                            } else if (pm10 <= 250) {
                                eva = "轻度污染";
                                progress = (int) ((pm10 - 150) / 6 + 200 / 6);
                            } else if (pm10 <= 350) {
                                eva = "中度污染";
                                progress = (int) ((pm10 - 250) / 6 + 300 / 6);
                            } else if (pm10 <= 420) {
                                eva = "重度污染";
                                progress = (int) ((pm10 - 350) / 420 + 400 / 6);
                            } else {
                                eva = "严重污染";
                                progress = 90;
                            }
                            data_air.get(4).put("Evaluate", eva);     //PM10
                            data_air.get(4).put("Progress", String.valueOf(progress));


                            //评估，室内信息部分

                            eva = "null";    //评估温度
                            progress = 0;
                            if (tmp <= 10) {
                                eva = "极寒";
                                progress = 50 / 6;
                            } else if (tmp <= 15) {
                                eva = "寒冷";
                                progress = (int) ((tmp - 10) / 5 * 100 / 6 + 100 / 6);
                            } else if (tmp <= 20) {
                                eva = "凉爽";
                                progress = (int) ((tmp - 15) / 5 * 100 / 6 + 200 / 6);
                            } else if (tmp <= 28) {
                                eva = "舒适";
                                progress = (int) ((tmp - 20) / 8 * 100 / 6 + 300 / 6);
                            } else if (tmp <= 34) {
                                eva = "闷热";
                                progress = (int) ((tmp - 28) / 6 * 100 / 6 + 400 / 6);
                            } else {
                                eva = "极热";
                                progress = 550 / 6;
                            }
                            data_room.get(0).put("Evaluate", eva);     //温度
                            data_room.get(0).put("Progress", String.valueOf(progress));

                            eva = "null";   //评估湿度
                            progress = 0;
                            if (humidity <= 40) {
                                eva = "干燥";
                                progress = (int) (humidity / 40.0 * 100 / 3.0);
                            } else if (humidity <= 70) {
                                eva = "舒适";
                                progress = (int) ((humidity - 40) / 30.0 * 100 / 3.0 + 100 / 3.0);
                            } else {
                                eva = "潮湿";
                                progress = (int) ((humidity - 70) / 30.0 * 100 / 3.0 + 200 / 3.0);
                            }
                            if(progress>90)
                                progress=90;
                            data_room.get(1).put("Evaluate", eva);     //湿度
                            data_room.get(1).put("Progress", String.valueOf(progress));

                            eva = "null";  //评估气压
                            progress = 0;
                            if (atm >= 110) {
                                eva = "气压高";
                                progress = 80;
                            } else if (atm <= 90) {
                                eva = "气压低";
                                progress = 20;
                            } else {
                                eva = "正常";
                                progress = 50;
                            }
                            data_room.get(2).put("Evaluate", eva);     //大气压
                            data_room.get(2).put("Progress", String.valueOf(progress));

                            data_room.get(3).put("Evaluate", staff == 1 ? "有人" : "无人");     //评估人员信息

                            eva = "null";   //评估光感
                            progress = 0;
                            if (light == 0) {
                                eva = "极弱";
                                progress = 10;
                            } else if (light == 1) {
                                eva = "适中";
                                progress = 30;
                            } else if (light == 2) {
                                eva = "强";
                                progress = 50;
                            } else if (light == 3) {
                                eva = "很强";
                                progress = 70;
                            } else {
                                eva = "极强";
                                progress = 90;
                            }
                            data_room.get(4).put("Evaluate", eva);     //光感
                            data_room.get(4).put("Progress", String.valueOf(progress));
                            adapter.notifyDataSetChanged();

                        }
                        else if(s.substring(12,14).equals("0c"))  //寄存器2，灯光信息，长度12个字节
                        {
                            if(which.equals("2"))
                            {
                                which="100";  //标志位，置100
                                Message message = new Message();
                                message.what =0;       //0表示要隐藏
                                handler2.sendMessage(message);
                            }
                            state_r=a[8]&0xff;    //红灯开关状态
                            state_g=a[12]&0xff;     //绿灯开关状态
                            state_b=a[16]&0xff;    //蓝灯开关状态
                            progress_r=a[10]&0xff;     //红灯pwm大小
                            progress_g=a[14]&0xff;     //绿灯PWM
                            progress_b=a[18]&0xff;    //蓝灯PWM
                            //  ListInit();
                            if(switch_b!=null)
                            {
                                seekbar_r.setProgress(progress_r);
                                seekbar_g.setProgress(progress_g);
                                seekbar_b.setProgress(progress_b);
                                switch_r.setState(state_r==1?true:false);
                                switch_g.setState(state_g==1?true:false);
                                switch_b.setState(state_b==1?true:false);
                            }
                        }
                    }
                    else if(s.substring(10,12).equals("06"))   //如果是控制单个寄存器返回命令，则可能是报警信息，也可能是修改语音开关、报警开关返回的数据
                    {
                        if(s.substring(12,16).equals("0305"))   //报警信息，判断是哪个参数在报警,a[8]是高八位，a[9]是低八位
                        {
                            String str_warning="";   //要报警的内容
                            int If_warning=0  ;   //是否要报警
                            if((a[9]&bits[0])==bits[0]&&warning_state[0].equals("1"))    //振动报警，如果相等则表示该位报警
                            {
                                If_warning=1;
                                str_warning+="振动强度";
                            }
                            if((a[9]&bits[1])==bits[1]&&warning_state[1].equals("1"))    //PM2.5报警，如果相等则表示该位报警
                            {
                                if(If_warning==1)
                                    str_warning+="、PM2.5";
                                else
                                    str_warning += "PM2.5";
                                If_warning=1;
                            }
                            if((a[9]&bits[2])==bits[2]&&warning_state[2].equals("1"))    //甲醛报警，如果相等则表示该位报警
                            {
                                if(If_warning==1)
                                    str_warning+="、甲醛";
                                else
                                    str_warning += "甲醛";
                                If_warning=1;
                            }
                            if((a[9]&bits[3])==bits[3]&&warning_state[3].equals("1"))    //烟雾报警，如果相等则表示该位报警
                            {
                                if(If_warning==1)
                                    str_warning+="、烟雾";
                                else
                                    str_warning += "烟雾";
                                If_warning=1;
                            }
                            if((a[9]&bits[4])==bits[4]&&warning_state[4].equals("1"))    //温度报警，如果相等则表示该位报警
                            {
                                if(If_warning==1)
                                    str_warning+="、温度";
                                else
                                    str_warning += "温度";
                                If_warning=1;
                            }
                            if(If_warning==1) {
                                str_warning = "外间：" + str_warning + "超过预警值！";
                                Message msg1 = new Message();
                                msg1.obj=str_warning;
                                handler3.sendMessage(msg1);   //通知handler3进行报警
                            }

                        }
                        else if(s.substring(12, 16).equals("0200"))  //地址为0200，是红灯开关
                        {
                            if(which.equals("3"))
                            {
                                which="100";

                                Message message = new Message();
                                message.what =0;       //0表示要隐藏
                                handler2.sendMessage(message);
                            }
                            state_r=(s.substring(19, 20).equals("1"))?1:0;
                            switch_r.setState(state_r==1?true:false);
                            //ListInit();
                        }
                        else if(s.substring(12, 16).equals("0202"))  //地址为0202，是绿灯开关
                        {
                            if(which.equals("4"))
                            {
                                which="100";

                                Message message = new Message();
                                message.what =0;       //0表示要隐藏
                                handler2.sendMessage(message);
                            }
                            state_g=(s.substring(19, 20).equals("0"))?0:1;
                            switch_g.setState(state_g==1?true:false);
                            //ListInit();
                        }
                        else if(s.substring(12, 16).equals("0204"))  //地址为0204，是蓝灯开关
                        {
                            if(which.equals("5"))
                            {
                                which="100";

                                Message message = new Message();
                                message.what =0;       //0表示要隐藏
                                handler2.sendMessage(message);
                            }
                            state_b=(s.substring(19, 20).equals("1"))?1:0;
                            switch_b.setState(state_b==1?true:false);
                            // ListInit();
                        }
                    }
                }
                else if (IsEffective == 2)   //如果数据有效，则进行解析，并更新页面.里间
                {
                    if (s.substring(10, 12).equals("04")||s.substring(10,12).equals("03"))   //如果是读寄存器状态，则判断是读寄存器1（室内参数），还是寄存器2（灯光信息）的状态
                    {
                        if (s.substring(12, 14).equals("22")&&a.length>40)  //寄存器1，长度34个字节
                        {
                            //计算得到各个参数的值，顺序是按寄存器顺序来的
                            double tmp_old=((a[7] + 256) % 256) * 256 + (a[8] + 256) % 256;   //原始温度，即正常温度的100倍
                            if(tmp_old>30000)
                                tmp_old=tmp_old-65536;   //负温度值
                            double tmp = tmp_old/ 100.0;   //温度
                            double humidity = (((a[13] + 256) % 256) * 256 + (a[14] + 256) % 256)/10.0 ;   //湿度
                            while(humidity>100)
                                 humidity=humidity/10.0;    //防止湿度出现大于100的数字
                            double atm = (((a[15] + 256) % 256) * 256 + (a[16] + 256) % 256) / 100.0;       //大气压
                            double arofene = (((a[17] + 256) % 256) * 256 + (a[18] + 256) % 256) / 1000.0;    //甲醛
                            double smoke = (((a[19] + 256) % 256) * 256 + (a[20] + 256) % 256);       //烟雾
                            double staff = (((a[21] + 256) % 256) * 256 + (a[22] + 256) % 256);     //人员信息
                            double light = (((a[23] + 256) % 256) * 256 + (a[24] + 256) % 256);  //光感
                            double pm1 = (((a[25] + 256) % 256) * 256 + (a[26] + 256) % 256);     //PM1
                            double pm25 = (((a[27] + 256) % 256) * 256 + (a[28] + 256) % 256);     //PM2.5
                            double pm10 = (((a[29] + 256) % 256) * 256 + (a[30] + 256) % 256);     //PM10
                            double xdegree= (((a[33] + 256) % 256) * 256 + (a[34] + 256) % 256)/100.0;   //X轴角度
                            double ydegree= (((a[35] + 256) % 256) * 256 + (a[36] + 256) % 256)/100.0;  //Y轴角度
                            double zdegree= (((a[37] + 256) % 256) * 256 + (a[38] + 256) % 256)/100.0;   //水平角度
                            double shake=(((a[39] + 256) % 256) * 256 + (a[40] + 256) % 256);   //振动强度

                            String switch_shake = (a[32] & bits[0]) == bits[0] ? "1" : "0";     //振动报警开关状态
                            String switch_pm25 = (a[32] & bits[1]) == bits[1] ? "1" : "0";        //PM2.5报警开关状态
                            String switch_arofene = (a[32] & bits[2]) == bits[2] ? "1" : "0";      //甲醛报警开关状态
                            String switch_smoke = (a[32] & bits[3]) == bits[3] ? "1" : "0";        //烟雾报警开关状态
                            String switch_tmp = (a[32] & bits[4]) == bits[4] ? "1" : "0";

                            data_room1.get(0).put("Value", String.valueOf(tmp) + " ℃");  //温度值
                            data_room1.get(1).put("Value", String.valueOf(humidity) + " %RH");  //湿度
                            data_room1.get(2).put("Value", String.valueOf(atm) + " kPa");   //大气压
                            data_room1.get(3).put("Value", String.valueOf(staff) + " ");     //人员信息
                            data_room1.get(4).put("Value", String.valueOf(light) + " ");    //光感

                            data_air1.get(0).put("Value", String.valueOf(smoke) + " ppm");    //烟雾
                            data_air1.get(1).put("Value", String.valueOf(arofene) + " ppm");   //甲醛
                            data_air1.get(2).put("Value", String.valueOf(pm1) + " ");     //PM1
                            data_air1.get(3).put("Value", String.valueOf(pm25) + " ");   //PM2.5
                            data_air1.get(4).put("Value", String.valueOf(pm10) + " ");   //PM10

                            data_posture1.get(0).put("Value", String.valueOf(xdegree) + " °"); //x轴角度
                            data_posture1.get(1).put("Value", String.valueOf(ydegree) + " °");  //y轴角度
                            if(zdegree>10)
                                data_posture1.get(2).put("Value", String.valueOf(zdegree) + " °(倾斜)");   //水平角度
                            else
                                data_posture1.get(2).put("Value", String.valueOf(zdegree) + " °(正常)");   //水平角度
                            data_posture1.get(3).put("Value", String.valueOf(shake) );//振动强度

                            warning_state1[0]=switch_shake;
                            warning_state1[1]=switch_pm25;
                            warning_state1[2]=switch_arofene;
                            warning_state1[3]=switch_smoke;
                            warning_state1[4]=switch_tmp;
                            //评估，空气质量部分
                            String eva = "null";//评估，优、良、轻度污染、中度污染、重度污染、严重污染
                            int progress = 0;//进度
                            if (smoke <= 750) {
                                eva = "清洁";
                                progress = (int) (smoke / 750 * 100 / 6);
                            } else {
                                eva = "污染";
                                progress = (int) (100 / 6 + (smoke - 750) * 500 / 9250 / 6);
                            }
                            data_air1.get(0).put("Evaluate", eva);     //烟雾0-10000
                            data_air1.get(0).put("Progress", String.valueOf(progress));

                            eva = "null";   //评估甲醛，0-1000
                            progress = 0;
                            if (arofene <= 0.1) {
                                eva = "清洁";
                                progress = (int) (arofene / 0.1 * 100 / 6);
                            } else {
                                eva = "超标";
                                progress = (int) (100 / 6 + (arofene - 0.1) * 500 / 6);
                                if (progress >= 80)
                                    progress = 80;
                            }
                            data_air1.get(1).put("Evaluate", eva);     //甲醛，假设是0-1
                            data_air1.get(1).put("Progress", String.valueOf(progress));

                            eva = "null";
                            progress = 0;
                            //评估,PM1
                            if (pm1 <= 35) {
                                eva = "优";
                                progress = (int) (pm1 / 35 / 6 * 100);
                            } else if (pm1 <= 75) {
                                eva = "良";
                                progress = (int) ((pm1 - 35) / 240 * 100 + 100 / 6);
                            } else if (pm1 <= 115) {
                                eva = "轻度污染";
                                progress = (int) ((pm1 - 75) / 240 * 100 + 200 / 6);
                            } else if (pm1 <= 150) {
                                eva = "中度污染";
                                progress = (int) ((pm1 - 115) / 35 / 6 * 100 + 300 / 6);
                            } else if (pm1 <= 250) {
                                eva = "重度污染";
                                progress = (int) ((pm1 - 150) / 6 + 400 / 6);
                            } else {
                                eva = "严重污染";
                                progress = 90;
                            }
                            data_air1.get(2).put("Evaluate", eva);     //PM1
                            data_air1.get(2).put("Progress", String.valueOf(progress));


                            eva = "null";  //评估,PM2.5
                            progress = 0;
                            if (pm25 <= 35) {
                                eva = "优";
                                progress = (int) (pm25 / 35 / 6 * 100);
                            } else if (pm25 <= 75) {
                                eva = "良";
                                progress = (int) ((pm25 - 35) / 240 * 100 + 100 / 6);
                            } else if (pm25 <= 115) {
                                eva = "轻度污染";
                                progress = (int) ((pm25 - 75) / 240 * 100 + 200 / 6);
                            } else if (pm25 <= 150) {
                                eva = "中度污染";
                                progress = (int) ((pm25 - 115) / 35 / 6 * 100 + 300 / 6);
                            } else if (pm25 <= 250) {
                                eva = "重度污染";
                                progress = (int) ((pm25 - 150) / 6 + 400 / 6);
                            } else {
                                eva = "严重污染";
                                progress = 90;
                            }
                            data_air1.get(3).put("Evaluate", eva);     //PM2.5
                            data_air1.get(3).put("Progress", String.valueOf(progress));
                            // Log.i("Order","计算得到："+String.valueOf(progress));

                            eva = "null";  //评估,PM10
                            progress = 0;
                            if (pm10 <= 50) {
                                eva = "优";
                                progress = (int) (pm10 / 50 * 100 / 6);
                            } else if (pm10 <= 150) {
                                eva = "良";
                                progress = (int) ((pm10 - 50) / 6 + 100 / 6);
                            } else if (pm10 <= 250) {
                                eva = "轻度污染";
                                progress = (int) ((pm10 - 150) / 6 + 200 / 6);
                            } else if (pm10 <= 350) {
                                eva = "中度污染";
                                progress = (int) ((pm10 - 250) / 6 + 300 / 6);
                            } else if (pm10 <= 420) {
                                eva = "重度污染";
                                progress = (int) ((pm10 - 350) / 420 + 400 / 6);
                            } else {
                                eva = "严重污染";
                                progress = 90;
                            }
                            data_air1.get(4).put("Evaluate", eva);     //PM10
                            data_air1.get(4).put("Progress", String.valueOf(progress));


                            //评估，室内信息部分

                            eva = "null";    //评估温度
                            progress = 0;
                            if (tmp <= 10) {
                                eva = "极寒";
                                progress = 50 / 6;
                            } else if (tmp <= 15) {
                                eva = "寒冷";
                                progress = (int) ((tmp - 10) / 5 * 100 / 6 + 100 / 6);
                            } else if (tmp <= 20) {
                                eva = "凉爽";
                                progress = (int) ((tmp - 15) / 5 * 100 / 6 + 200 / 6);
                            } else if (tmp <= 28) {
                                eva = "舒适";
                                progress = (int) ((tmp - 20) / 8 * 100 / 6 + 300 / 6);
                            } else if (tmp <= 34) {
                                eva = "闷热";
                                progress = (int) ((tmp - 28) / 6 * 100 / 6 + 400 / 6);
                            } else {
                                eva = "极热";
                                progress = 550 / 6;
                            }
                            data_room1.get(0).put("Evaluate", eva);     //温度
                            data_room1.get(0).put("Progress", String.valueOf(progress));

                            eva = "null";   //评估湿度
                            progress = 0;
                            if (humidity <= 40) {
                                eva = "干燥";
                                progress = (int) (humidity / 40.0 * 100 / 3.0);
                            } else if (humidity <= 70) {
                                eva = "舒适";
                                progress = (int) ((humidity - 40) / 30.0 * 100 / 3.0 + 100 / 3.0);
                            } else {
                                eva = "潮湿";
                                progress = (int) ((humidity - 70) / 30.0 * 100 / 3.0 + 200 / 3.0);
                            }
//                            if(progress>90)
//                                progress=90;
                            data_room1.get(1).put("Evaluate", eva);     //湿度
                            data_room1.get(1).put("Progress", String.valueOf(progress));

                            eva = "null";  //评估气压
                            progress = 0;
                            if (atm >= 110) {
                                eva = "气压高";
                                progress = 80;
                            } else if (atm <= 90) {
                                eva = "气压低";
                                progress = 20;
                            } else {
                                eva = "正常";
                                progress = 50;
                            }
                            data_room1.get(2).put("Evaluate", eva);     //大气压
                            data_room1.get(2).put("Progress", String.valueOf(progress));

                            data_room1.get(3).put("Evaluate", staff == 1 ? "有人" : "无人");     //评估人员信息

                            eva = "null";   //评估光感
                            progress = 0;
                            if (light == 0) {
                                eva = "极弱";
                                progress = 10;
                            } else if (light == 1) {
                                eva = "适中";
                                progress = 30;
                            } else if (light == 2) {
                                eva = "强";
                                progress = 50;
                            } else if (light == 3) {
                                eva = "很强";
                                progress = 70;
                            } else {
                                eva = "极强";
                                progress = 90;
                            }
                            data_room1.get(4).put("Evaluate", eva);     //光感
                            data_room1.get(4).put("Progress", String.valueOf(progress));
                            adapter.notifyDataSetChanged();

                        }
                        else if(s.substring(12,14).equals("0c"))  //寄存器2，灯光信息，长度12个字节
                        {
                            if(which.equals("2"))
                            {
                                which="100";  //标志位，置100
                                Message message = new Message();
                                message.what =0;       //0表示要隐藏
                                handler2.sendMessage(message);
                            }
                            state_r1=a[8]&0xff;    //红灯开关状态
                            state_g1=a[12]&0xff;     //绿灯开关状态
                            state_b1=a[16]&0xff;    //蓝灯开关状态
                            progress_r1=a[10]&0xff;     //红灯pwm大小
                            progress_g1=a[14]&0xff;     //绿灯PWM
                            progress_b1=a[18]&0xff;    //蓝灯PWM
                            //  ListInit();
                            if(switch_b1!=null)
                            {
                                seekbar_r1.setProgress(progress_r1);
                                seekbar_g1.setProgress(progress_g1);
                                seekbar_b1.setProgress(progress_b1);
                                switch_r1.setState(state_r1==1?true:false);
                                switch_g1.setState(state_g1==1?true:false);
                                switch_b1.setState(state_b1==1?true:false);
                            }
                        }
                    }
                    else if(s.substring(10,12).equals("06"))   //如果是控制单个寄存器返回命令，则可能是报警信息，也可能是修改语音开关、报警开关返回的数据
                    {
                        if(s.substring(12,16).equals("0305"))   //报警信息，判断是哪个参数在报警,a[8]是高八位，a[9]是低八位
                        {
                            String str_warning="";   //要报警的内容
                            int If_warning=0  ;   //是否要报警
                            if((a[9]&bits[0])==bits[0]&&warning_state1[0].equals("1"))    //振动报警，如果相等则表示该位报警，并且允许报警
                            {
                                If_warning=1;
                                str_warning+="振动强度";
                            }
                            if((a[9]&bits[1])==bits[1]&&warning_state1[1].equals("1"))    //PM2.5报警，如果相等则表示该位报警
                            {
                                if(If_warning==1)
                                    str_warning+="、PM2.5";
                                else
                                    str_warning += "PM2.5";
                                If_warning=1;
                            }
                            if((a[9]&bits[2])==bits[2]&&warning_state1[2].equals("1"))    //甲醛报警，如果相等则表示该位报警
                            {
                                if(If_warning==1)
                                    str_warning+="、甲醛";
                                else
                                    str_warning += "甲醛";
                                If_warning=1;
                            }
                            if((a[9]&bits[3])==bits[3]&&warning_state1[3].equals("1"))    //烟雾报警，如果相等则表示该位报警
                            {
                                if(If_warning==1)
                                    str_warning+="、烟雾";
                                else
                                    str_warning += "烟雾";
                                If_warning=1;
                            }
                            if((a[9]&bits[4])==bits[4]&&warning_state1[4].equals("1"))    //温度报警，如果相等则表示该位报警
                            {
                                if(If_warning==1)
                                    str_warning+="、温度";
                                else
                                    str_warning += "温度";
                                If_warning=1;
                            }
                            if(If_warning==1) {
                                str_warning = "里间：" + str_warning + "超过预警值！";
                                Message msg1 = new Message();
                                msg1.obj=str_warning;
                                handler3.sendMessage(msg1);   //通知handler3进行报警
                            }

                        }
                        else if(s.substring(12, 16).equals("0200"))  //地址为0200，是红灯开关
                        {
                            if(which.equals("3"))
                            {
                                which="100";

                                Message message = new Message();
                                message.what =0;       //0表示要隐藏
                                handler2.sendMessage(message);
                            }
                            state_r1=(s.substring(19, 20).equals("1"))?1:0;
                            switch_r1.setState(state_r1==1?true:false);
                            //ListInit();
                        }
                        else if(s.substring(12, 16).equals("0202"))  //地址为0202，是绿灯开关
                        {
                            if(which.equals("4"))
                            {
                                which="100";

                                Message message = new Message();
                                message.what =0;       //0表示要隐藏
                                handler2.sendMessage(message);
                            }
                            state_g1=(s.substring(19, 20).equals("0"))?0:1;
                            switch_g1.setState(state_g1==1?true:false);
                            //ListInit();
                        }
                        else if(s.substring(12, 16).equals("0204"))  //地址为0204，是蓝灯开关
                        {
                            if(which.equals("5"))
                            {
                                which="100";

                                Message message = new Message();
                                message.what =0;       //0表示要隐藏
                                handler2.sendMessage(message);
                            }
                            state_b1=(s.substring(19, 20).equals("1"))?1:0;
                            switch_b1.setState(state_b1==1?true:false);
                            // ListInit();
                        }
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
                        Toast.makeText(getActivity(), "网络错误！", Toast.LENGTH_SHORT).show();
                    }
                    //	ListInit();
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
    private AlertDialog dialog;
    private AlertDialog.Builder builder;
    //handler3用于显示报警
    Handler handler3=new Handler(){
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            String str_warning=msg.obj.toString();
            if(dialog!=null)
            {
                try {
                    dialog.dismiss();
                }
                catch(Exception ex){ex.printStackTrace();}
            }
            builder = new AlertDialog.Builder(getActivity());
            LayoutInflater factory = LayoutInflater.from(getActivity());
            final View view = factory.inflate(R.layout.warning, null);
            TextView tx_warning = (TextView) view.findViewById(R.id.tx_warning);   //报警信息
            tx_warning.setText(str_warning);
            builder.setCancelable(true);
            builder.setView(view);
            dialog=builder.create();
            dialog.show();
            Button button = (Button) view.findViewById(R.id.button);
            button.setOnTouchListener(new TouchListener());
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    if (dialog != null)
                        dialog.dismiss();
                }
            });
            // 震动效果的系统服务
            Vibrator vibrator = (Vibrator) getActivity().getSystemService(getActivity().VIBRATOR_SERVICE);
            vibrator.vibrate(2000);//振动两秒
            try {    //播放系统通知声音
                Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                MediaPlayer player = new MediaPlayer();
                player.setDataSource(getActivity(), alert);
                final AudioManager audioManager = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
                if (audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION) != 0) {
                    player.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
                    player.setLooping(false);   //是否循环播放
                    player.prepare();   //准备音频文件，相当于缓存
                    player.start();      //开始播放
                }
            }
            catch (IOException e){}

        }
    };
    @Override     //当前页面可见与不可见的状态
    public void setUserVisibleHint(boolean isVisibleToUser) {   //这里的可见与不可见只在fragment切换时发生变化。
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser)
        {    //可见时
            try
            {
                // Log.i("Order", "parameter可见");
                isVisible=1;
                mPullRefreshListView.setRefreshing();
            }
            catch (Exception e)
            {
                // TODO Auto-generated catch block
                isVisible=0;
                e.printStackTrace();
            }
            //相当于Fragment的onResume
        }
        else     //不可见时
        {
            //相当于Fragment的onPause    ,关闭socket连接
            try
            {
                Log.i("Order", "parameter不可见");

            }
            catch (Exception e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
    @Override
    public void onDestroyView()
    {

        try
        {
            Log.i("Order","parameter销毁");
          //  getActivity().unbindService(con);   //解除Service的绑定
        }
        catch (Exception e)
        {
            Log.i("Order","parameter销毁出错");
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        super.onDestroyView();
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        Log.i("Order", "Parameter==>onCreateView");
        DataInit();    //数据初始化
        View view = inflater.inflate(R.layout.parameter, null);
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);//获取屏幕大小的信息
        Pwidth=displayMetrics.widthPixels;   //屏幕宽度,先锋的宽度是800px，小米2a的宽度是720px
       // Toast.makeText(getActivity().getApplication(), String.valueOf(Converts.px2dip(getActivity().getApplicationContext(),(int)Pwidth)), Toast.LENGTH_SHORT).show();
        int pwidth = Converts.px2dip(getActivity().getApplicationContext(), (int) Pwidth);
        if(pwidth>450)
            device="平板";
        else
            device="手机";
        mPullRefreshListView = (PullToRefreshListView)view.findViewById(R.id.list);   //下拉列表控件
        list=mPullRefreshListView.getRefreshableView();   //从下拉列表控件中获取
        mPullRefreshListView.setMode(Mode.PULL_FROM_START);//只有下拉刷新

        WidgetInit();   //控件初始化
        Log.v("Time","Parameter初始化完成");
        return view;
    }

    //数据初始化，初始化各个参数名称
    private void DataInit()
    {
        //首先是外间的
        //室内环境，5个参数：温度、湿度、大气压、人员信息、光线强度
        Map<String,String> map1=new HashMap<String,String>();
        map1.put("Name", "温度");     //参数名称
        map1.put("Value", "null ℃");         //值
        map1.put("Evaluate", "null");    //评估，冷、热等
        map1.put("Progress", "0");
        data_room.add(map1);

        Map<String,String> map2=new HashMap<String,String>();
        map2.put("Name", "湿度");
        map2.put("Value", "null %RH");
        map2.put("Evaluate", "null");
        map2.put("Progress", "0");
        data_room.add(map2);

        Map<String,String> map3=new HashMap<String,String>();
        map3.put("Name", "大气压");
        map3.put("Value", "null kPa");
        map3.put("Evaluate", "null");
        map3.put("Progress", "0");
        data_room.add(map3);


        Map<String,String> map10=new HashMap<String,String>();
        map10.put("Name", "人员信息");
        map10.put("Value", "0");
        map10.put("Evaluate", "null");
        map10.put("Progress", "0");
        data_room.add(map10);

        Map<String,String> map6=new HashMap<String,String>();
        map6.put("Name", "光线强度");
        map6.put("Value", "null");
        map6.put("Evaluate", "null");
        map6.put("Progress", "0");
        data_room.add(map6);


        //环境质量，5个参数：甲醛、烟雾、PM1，PM2.5，PM10
        Map<String,String> map5=new HashMap<String,String>();
        map5.put("Name", "烟雾");
        map5.put("Value", "null ppm");
        map5.put("Evaluate", "null");
        map5.put("Progress", "0");
        data_air.add(map5);

        Map<String,String> map4=new HashMap<String,String>();
        map4.put("Name", "甲醛");
        map4.put("Value", "null ppm");
        map4.put("Evaluate", "null");
        map4.put("Progress", "0");    //占颜色标准条的比例
        data_air.add(map4);

        Map<String,String> map7=new HashMap<String,String>();
        map7.put("Name", "PM1");
        map7.put("Value", "null");
        map7.put("Evaluate", "null");
        map7.put("Progress", "0");
        data_air.add(map7);

        Map<String,String> map8=new HashMap<String,String>();
        map8.put("Name", "PM2.5");
        map8.put("Value", "null");
        map8.put("Evaluate", "null");
        map8.put("Progress", "0");
        data_air.add(map8);

        Map<String,String> map9=new HashMap<String,String>();
        map9.put("Name", "PM10");
        map9.put("Value", "null");
        map9.put("Evaluate", "null");
        map9.put("Progress", "0");
        data_air.add(map9);

      //  姿态信息4个参数，x轴角度，y轴角度，水平角度，振动强度
        Map<String, String> map30 = new HashMap<>();
        map30.put("Name", "X轴角度");
        map30.put("Value", "null");
        data_posture.add(map30);

        Map<String, String> map31 = new HashMap<>();
        map31.put("Name", "y轴角度");
        map31.put("Value", "null");
        data_posture.add(map31);

        Map<String, String> map32 = new HashMap<>();
        map32.put("Name", "水平夹角");
        map32.put("Value", "null");
        data_posture.add(map32);

        Map<String, String> map33 = new HashMap<>();
        map33.put("Name", "振动强度等级");
        map33.put("Value", "null");
        data_posture.add(map33);

        //然后是里间的
        //室内环境，5个参数：温度、湿度、大气压、人员信息、光线强度
        Map<String,String> map11=new HashMap<String,String>();
        map11.put("Name", "温度");     //参数名称
        map11.put("Value", "null ℃");         //值
        map11.put("Evaluate", "null");    //评估，冷、热等
        map11.put("Progress", "0");
        data_room1.add(map11);

        Map<String,String> map12=new HashMap<String,String>();
        map12.put("Name", "湿度");
        map12.put("Value", "null %RH");
        map12.put("Evaluate", "null");
        map12.put("Progress", "0");
        data_room1.add(map12);

        Map<String,String> map13=new HashMap<String,String>();
        map13.put("Name", "大气压");
        map13.put("Value", "null kPa");
        map13.put("Evaluate", "null");
        map13.put("Progress", "0");
        data_room1.add(map13);


        Map<String,String> map21=new HashMap<String,String>();
        map21.put("Name", "人员信息");
        map21.put("Value", "0");
        map21.put("Evaluate", "null");
        map21.put("Progress", "0");
        data_room1.add(map21);

        Map<String,String> map16=new HashMap<String,String>();
        map16.put("Name", "光线强度");
        map16.put("Value", "null");
        map16.put("Evaluate", "null");
        map16.put("Progress", "0");
        data_room1.add(map16);


        //环境质量，5个参数：甲醛、烟雾、PM1，PM2.5，PM10
        Map<String,String> map15=new HashMap<String,String>();
        map15.put("Name", "烟雾");
        map15.put("Value", "null ppm");
        map15.put("Evaluate", "null");
        map15.put("Progress", "0");
        data_air1.add(map15);

        Map<String,String> map14=new HashMap<String,String>();
        map14.put("Name", "甲醛");
        map14.put("Value", "null ppm");
        map14.put("Evaluate", "null");
        map14.put("Progress", "0");    //占颜色标准条的比例
        data_air1.add(map14);

        Map<String,String> map17=new HashMap<String,String>();
        map17.put("Name", "PM1");
        map17.put("Value", "null");
        map17.put("Evaluate", "null");
        map17.put("Progress", "0");
        data_air1.add(map17);

        Map<String,String> map18=new HashMap<String,String>();
        map18.put("Name", "PM2.5");
        map18.put("Value", "null");
        map18.put("Evaluate", "null");
        map18.put("Progress", "0");
        data_air1.add(map18);

        Map<String,String> map19=new HashMap<String,String>();
        map19.put("Name", "PM10");
        map19.put("Value", "null");
        map19.put("Evaluate", "null");
        map19.put("Progress", "0");
        data_air1.add(map19);

     //   姿态信息4个参数，x轴角度，y轴角度，水平角度，振动强度
        Map<String, String> map40 = new HashMap<>();
        map40.put("Name", "X轴角度");
        map40.put("Value", "null");
        data_posture1.add(map40);

        Map<String, String> map41 = new HashMap<>();
        map41.put("Name", "y轴角度");
        map41.put("Value", "null");
        data_posture1.add(map41);

        Map<String, String> map42 = new HashMap<>();
        map42.put("Name", "水平夹角");
        map42.put("Value", "null");
        data_posture1.add(map42);

        Map<String, String> map43 = new HashMap<>();
        map43.put("Name", "振动强度等级");
        map43.put("Value", "null");
        data_posture1.add(map43);
    }
    //控件初始化，为控件绑定监听函数，以及为listview设置适配器
    private void WidgetInit()
    {
        // 列表下拉监听
        mPullRefreshListView.setOnRefreshListener(new OnRefreshListener<ListView>() {
            @Override
            public void onRefresh(PullToRefreshBase<ListView> refreshView)
            {
                String label = DateUtils.formatDateTime(getActivity().getApplicationContext(), System.currentTimeMillis(),
                        DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_ALL);

                // Update the LastUpdatedLabel
                refreshView.getLoadingLayoutProxy().setLastUpdatedLabel("上次刷新："+label);
                // Do work to refresh the list here.
                new GetDataTask().execute();   //执行任务
            }
        });
        //	 list.setAdapter(new Adapter());    //为listview设置适配器

        ListInit();

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
           // SendOrder("f003 0100 0010",false);
            which="2";   //2表示查询所有参数状态
//            String addr;
//            if(((Main_Activity)getActivity()).flag_room.equals("外间"))
//                addr="0002";
//            else
//                addr="0003";
            String order="ab68"+addr+"f003 0100 0011";
            ((Main_Activity) getActivity()).binder.sendOrder(order,MainService.SIXSENSOR);
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            ((Main_Activity) getActivity()).binder.sendOrder("ab68"+addr+"f003 0200 0006",MainService.SIXSENSOR);
            return "1";
        }

        //这里是对刷新的响应，可以利用addFirst（）和addLast()函数将新加的内容加到LISTView中
        //根据AsyncTask的原理，onPostExecute里的result的值就是doInBackground()的返回值
        @Override
        protected void onPostExecute(String result)
        {
            if(result.equals("1"))  //请求数据成功，根据显示的页面重新初始化listview
            {

            }
            else            //请求数据失败
            {
                Toast.makeText(getActivity().getApplicationContext(), "加载失败！", Toast.LENGTH_SHORT).show();
            }
            // Call onRefreshComplete when the list has been refreshed.
            mPullRefreshListView.onRefreshComplete();   //表示刷新完成

            super.onPostExecute(result);//这句是必有的，AsyncTask规定的格式
        }
    }

    BaseAdapter adapter_time=new BaseAdapter() {
        @Override
        public int getCount() {
            return 1;
        }

        @Override
        public Object getItem(int position) {
            return "当前时间";
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null)
                convertView = LayoutInflater.from(getActivity().getApplication()).inflate(R.layout.parameter_timezone, null);
//                TextView name = (TextView) convertView.findViewById(R.id.name);
            TextView value = (TextView) convertView.findViewById(R.id.value);
//                name.setText("时区");
//                value.setText("中国，北京\nGMT+8:00");
            //获取系统当前时间
            SimpleDateFormat    sDateFormat    =   new SimpleDateFormat("yyyy年MM月dd日 HH:mm");   //hh为小写是12小时制，为大写HH时时24小时制
            String    date    =    sDateFormat.format(new    java.util.Date());
            value.setText(date);
            return convertView;
        }
    };

    BaseAdapter adapter_room=new BaseAdapter() {
        @Override
        public int getCount() {
            // TODO Auto-generated method stub
            return data_room.size();
        }

        @Override
        public Object getItem(int position) {
            // TODO Auto-generated method stub
            return data_room.get(position);
        }

        @Override
        public long getItemId(int position) {
            // TODO Auto-generated method stub
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null)
                convertView = LayoutInflater.from(getActivity().getApplication()).inflate(R.layout.parameter_room, null);
            TextView name = (TextView) convertView.findViewById(R.id.name);
            TextView value = (TextView) convertView.findViewById(R.id.value);
            TextView evaluate = (TextView) convertView.findViewById(R.id.evaluate);
            ImageView standard = (ImageView) convertView.findViewById(R.id.standard);   //等级划分条
            ImageView arrow = (ImageView) convertView.findViewById(R.id.arrow);   //箭头
            ImageView img_person = (ImageView) convertView.findViewById(R.id.img_person);   //箭头
            //  LinearLayout layout_number = (LinearLayout) convertView.findViewById((R.id.layout_number));   //显示数字的布局
            LinearLayout layout_arrow = (LinearLayout) convertView.findViewById(R.id.layout_arrow);   //游标的布局
            LinearLayout layout_all = (LinearLayout) convertView.findViewById(R.id.layout_all);   //中间条的整个布局
            LinearLayout layout_person = (LinearLayout) convertView.findViewById(R.id.layout_person);   //显示小人图标的布局
            Map<String, String> map = data_room.get(position);
            int progress = Integer.valueOf(map.get("Progress"));  //0-100当前值所属的进度条的位置
            name.setText(map.get("Name"));    //参数名称
            value.setText(map.get("Value"));    //参数值，带单位
            //value.setText(map.get("Value")+"+"+ String.valueOf(progress));
            evaluate.setText(map.get("Evaluate"));   //参数值评估结果
            if (map.get("Name").equals("湿度"))  //3个等级
            {
                standard.setImageResource(R.drawable.standard_humidity);
                //   layout_all.setPadding(Pwidth / 10, 0, Pwidth / 10, 0);   //设置颜色条为原长度的3/5
                layout_arrow.setPadding(Pwidth * progress / 200, 0, 0, 0);
                //根据progress大小，<50则设置左边距，>50则设置右边距
                if(progress<50)
                {
                    value.setGravity(Gravity.LEFT);
                    value.setPadding(Pwidth * progress / 200, 0, 0, 0);   //设置左边距
                }
                else
                {
                    value.setGravity(Gravity.RIGHT);
                    value.setPadding(0, 0, Pwidth * (90 - progress) / 200, 0);  //设置右边距
                }
//
            } else if (map.get("Name").equals("光线强度"))  //5个等级
            {
                standard.setImageResource(R.drawable.standard_light);
                //  layout_all.setPadding(Pwidth / 20, 0, Pwidth / 20, 0);   //设置颜色条为原长度的4/5
                layout_arrow.setPadding(Pwidth * progress / 200, 0, 0, 0);
                if(progress<50)
                {
                    value.setGravity(Gravity.LEFT);
                    value.setPadding(Pwidth * progress / 200, 0, 0, 0);   //设置左边距
                }
                else
                {
                    value.setGravity(Gravity.RIGHT);
                    value.setPadding(0, 0, Pwidth * (90 - progress) / 200, 0);  //设置右边距
                }
            } else if (map.get("Name").equals("大气压"))  //3个等级
            {
                standard.setImageResource(R.drawable.standard_humidity);
                layout_arrow.setPadding(Pwidth * progress / 200, 0, 0, 0);
                if(progress<50)
                {
                    value.setGravity(Gravity.LEFT);
                    value.setPadding(Pwidth * progress / 200, 0, 0, 0);   //设置左边距
                }
                else
                {
                    value.setGravity(Gravity.RIGHT);
                    value.setPadding(0, 0, Pwidth * (90 - progress) / 200, 0);  //设置右边距
                }
            } else if (map.get("Name").equals("温度"))  //6个等级
            {
                standard.setImageResource(R.drawable.standard_tem);
                // layout_all.setPadding(0, 0, 0, 0);
                layout_arrow.setPadding(Pwidth * progress / 200, 0, 0, 0);
                if(progress<50)
                {
                    value.setGravity(Gravity.LEFT);
                    value.setPadding(Pwidth * progress / 200, 0, 0, 0);   //设置左边距
                }
                else
                {
                    value.setGravity(Gravity.RIGHT);
                    value.setPadding(0, 0, Pwidth * (90 - progress) / 200, 0);  //设置右边距
                }
            } else if (map.get("Name").equals("人员信息"))  //只显示一个小人的图标
            {
                if (Double.valueOf(map.get("Value")) == 0)   //无人
                {
                    img_person.setImageResource(R.drawable.person0);
                    //layout_arrow.setVisibility(View.GONE);
                } else    //有人
                {
                    img_person.setImageResource(R.drawable.person1);
                    //layout_arrow.setVisibility(View.GONE);
                }
                layout_all.setVisibility(View.GONE);
                layout_person.setVisibility(View.VISIBLE);

            } else   //剩余的，x轴夹角，y轴夹角，水平夹角，振动情况什么的。。
            {
                arrow.setVisibility(View.GONE);
                standard.setVisibility(View.GONE);
            }
            return convertView;
        }
    };
    BaseAdapter adapter_air=new BaseAdapter() {
        @Override
        public int getCount() {
            // TODO Auto-generated method stub
            return data_air.size();
        }

        @Override
        public Object getItem(int position) {
            // TODO Auto-generated method stub
            return data_air.get(position);
        }

        @Override
        public long getItemId(int position) {
            // TODO Auto-generated method stub
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null)
                convertView = LayoutInflater.from(getActivity().getApplication()).inflate(R.layout.parameter_listview, null);
            TextView name = (TextView) convertView.findViewById(R.id.name);
            TextView value = (TextView) convertView.findViewById(R.id.value);
            TextView evaluate = (TextView) convertView.findViewById(R.id.evaluate);
            ImageView standard = (ImageView) convertView.findViewById(R.id.standard);   //等级划分条
            // LinearLayout layout_number = (LinearLayout) convertView.findViewById(R.id.layout_number);// 值
            LinearLayout layout_arrow = (LinearLayout) convertView.findViewById(R.id.layout_arrow);   //游标的布局
            LinearLayout layout_all = (LinearLayout) convertView.findViewById(R.id.layout_all);   //中间条的
            Map<String, String> map = data_air.get(position);
				/*int width=standard.getWidth();     //标准条的总长度
				int layout_width=layout_all.getWidth();     //区域总长度
*/
            int progress = Integer.valueOf(map.get("Progress"));
            name.setText(map.get("Name"));
            value.setText(map.get("Value"));
            //value.setText(map.get("Value")+"+"+ String.valueOf(progress));
            evaluate.setText(map.get("Evaluate"));
            if (map.get("Name").equals("烟雾"))   //两个等级
            {
                standard.setImageResource(R.drawable.standard_smoke);
                // layout_all.setPadding(Pwidth / 10, 0, Pwidth / 10, 0);   //设置颜色条为原长度的3/5
                layout_arrow.setPadding(Pwidth * progress  / 200, 0, 0, 0);
                if(progress<50)
                {
                    value.setGravity(Gravity.LEFT);
                    value.setPadding(Pwidth * progress / 200, 0, 0, 0);   //设置左边距
                }
                else
                {
                    value.setGravity(Gravity.RIGHT);
                    value.setPadding(0, 0, Pwidth * (90 - progress) / 200, 0);  //设置右边距
                }

            } else if (map.get("Name").equals("甲醛"))  //4个等级
            {
                standard.setImageResource(R.drawable.standard_smoke);
                //layout_all.setPadding(Pwidth / 10, 0, Pwidth / 10, 0);   //设置颜色条为原长度的3/5
                layout_arrow.setPadding(Pwidth * progress  / 200, 0, 0, 0);
                if(progress<50)
                {
                    value.setGravity(Gravity.LEFT);
                    value.setPadding(Pwidth * progress / 200, 0, 0, 0);   //设置左边距
                }
                else
                {
                    value.setGravity(Gravity.RIGHT);
                    value.setPadding(0, 0, Pwidth * (90 - progress) / 200, 0);  //设置右边距
                }
            }//其他都是6个等级
            else {
                layout_arrow.setPadding(Pwidth * progress / 200, 0, 0, 0);
                if(progress<50)
                {
                    value.setGravity(Gravity.LEFT);
                    value.setPadding(Pwidth * progress / 200, 0, 0, 0);   //设置左边距
                }
                else
                {
                    value.setGravity(Gravity.RIGHT);
                    value.setPadding(0, 0, Pwidth * (90 - progress) / 200, 0);  //设置右边距
                }
            }

            return convertView;
        }
    };

    BaseAdapter adapter_posture=new BaseAdapter() {
        @Override
        public int getCount() {
            // TODO Auto-generated method stub
            return data_posture.size();
        }

        @Override
        public Object getItem(int position) {
            // TODO Auto-generated method stub
            return data_posture.get(position);
        }

        @Override
        public long getItemId(int position) {
            // TODO Auto-generated method stub
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null)
                convertView = LayoutInflater.from(getActivity().getApplication()).inflate(R.layout.parameter_posture, null);
            TextView name = (TextView) convertView.findViewById(R.id.name);
            TextView value = (TextView) convertView.findViewById(R.id.value);
            Map<String, String> map = data_posture.get(position);
            name.setText(map.get("Name"));
            value.setText(map.get("Value"));
            return convertView;
        }
    };

    BaseAdapter adapter_lights=new BaseAdapter(){
        @Override
        public int getCount() {
            // TODO Auto-generated method stub
            return 1;
        }
        @Override
        public Object getItem(int position) {
            // TODO Auto-generated method stub
            return 1;
        }
        @Override
        public long getItemId(int position) {
            // TODO Auto-generated method stub
            return position;
        }
        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if(convertView==null)
                convertView = LayoutInflater.from(getActivity().getApplication()).inflate(R.layout.parameter_lights, null);
            seekbar_r=(SeekBar)convertView.findViewById(R.id.seekbar_r);   //红灯滚动条
            seekbar_g=(SeekBar)convertView.findViewById(R.id.seekbar_g);   //绿灯滚动条
            seekbar_b=(SeekBar)convertView.findViewById(R.id.seekbar_b);   //蓝灯滚动条
            switch_r=(Switch)convertView.findViewById(R.id.switch_r);    //红灯开关
            switch_g=(Switch)convertView.findViewById(R.id.switch_g);    //红灯开关
            switch_b=(Switch)convertView.findViewById(R.id.switch_b);    //红灯开关
            switch_r.setState(state_r==1?true:false);
            switch_g.setState(state_g==1?true:false);
            switch_b.setState(state_b==1?true:false);
            seekbar_r.setProgress(progress_r);
            seekbar_g.setProgress(progress_g);
            seekbar_b.setProgress(progress_b);
            seekbar_r.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                //为Seekbar设置状态改变监听
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress,
                                              boolean fromUser) {
                    // TODO Auto-generated method stub
                    progress_r=progress;
                    byte byt=(byte)progress;
                    String str=Converts.Bytes2HexString(new byte[]{byt});
                    ((Main_Activity)getActivity()).binder.sendOrder("ab68"+ Address.addr_out+"f006 0201 00" + str, MainService.SIXSENSOR);
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // TODO Auto-generated method stub
                }
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // TODO Auto-generated method stub
                    progress_r=seekBar.getProgress();
                    byte byt=(byte)progress_r;
                    String str=Converts.Bytes2HexString(new byte[]{byt});
                    ((Main_Activity)getActivity()).binder.sendOrder("ab68"+Address.addr_out+"f006 0203 00" + str, MainService.SIXSENSOR);
                }});
            seekbar_g.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                //为Seekbar设置状态改变监听
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress,
                                              boolean fromUser) {
                    // TODO Auto-generated method stub
                    progress_g=progress;
                    byte byt=(byte)progress;
                    String str=Converts.Bytes2HexString(new byte[]{byt});
                    ((Main_Activity)getActivity()).binder.sendOrder("ab68"+Address.addr_out+"f006 0203 00" + str, MainService.SIXSENSOR);

                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // TODO Auto-generated method stub
                }
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // TODO Auto-generated method stub
                    progress_g=seekBar.getProgress();
                    byte byt=(byte)progress_g;
                    String str=Converts.Bytes2HexString(new byte[]{byt});
                    ((Main_Activity)getActivity()).binder.sendOrder("ab68"+Address.addr_out+"f006 0203 00" + str, MainService.SIXSENSOR);
                }});
            seekbar_b.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                //为Seekbar设置状态改变监听
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress,
                                              boolean fromUser) {
                    // TODO Auto-generated method stub
                    progress_b=progress;
                    byte byt=(byte)progress;
                    String str=Converts.Bytes2HexString(new byte[]{byt});
                    ((Main_Activity)getActivity()).binder.sendOrder("ab68"+Address.addr_out+"f006 0205 00" + str, MainService.SIXSENSOR);

                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // TODO Auto-generated method stub
                }
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // TODO Auto-generated method stub
                    progress_b=seekBar.getProgress();
                    byte byt=(byte)progress_b;
                    String str=Converts.Bytes2HexString(new byte[]{byt});
                    ((Main_Activity)getActivity()).binder.sendOrder("ab68"+Address.addr_out+"f006 0203 00" + str,MainService.SIXSENSOR);
                }});
            switch_r.setOnChangeListener(new Switch.OnSwitchChangedListener(){   //红灯开关
                @Override
                public void onSwitchChange(Switch switchView, boolean isChecked) {
                    // TODO Auto-generated method stub
                    which="3";
                    ((Main_Activity)getActivity()).binder.sendOrder("ab68"+Address.addr_out+"f006 0200 000" + (isChecked ? "1" : "0"), MainService.SIXSENSOR);
						/*byte bt=(byte)progress_r;
						String str_r=Converts.Bytes2HexString(new byte[]{bt});   //红灯PWM
						bt=(byte)progress_g;
						String str_g=Converts.Bytes2HexString(new byte[]{bt});   //绿灯PWM
						bt=(byte)progress_b;
						String str_b=Converts.Bytes2HexString(new byte[]{bt});    //蓝灯PWM
						SendOrder("f010 0200 0006 0c 000"+(isChecked?"1":"0")+"00"+str_r+"000"+(switch_g.getState()?"1":"0")+"00"+str_g+"000"+(switch_b.getState()?"1":"0")+"00"+str_b,false);						*/
                }});
            switch_g.setOnChangeListener(new Switch.OnSwitchChangedListener(){   //绿灯开关
                @Override
                public void onSwitchChange(Switch switchView, boolean isChecked) {
                    // TODO Auto-generated method stub
							/*byte bt=(byte)progress_r;
							String str_r=Converts.Bytes2HexString(new byte[]{bt});   //红灯PWM
							bt=(byte)progress_g;
							String str_g=Converts.Bytes2HexString(new byte[]{bt});   //绿灯PWM
							bt=(byte)progress_b;
							String str_b=Converts.Bytes2HexString(new byte[]{bt});    //蓝灯PWM
							SendOrder("f010 0200 0006 0c 000"+(switch_r.getState()?"1":"0")+"00"+str_r+"000"+(isChecked?"1":"0")+"00"+str_g+"000"+(switch_b.getState()?"1":"0")+"00"+str_b,false);				*/
                    which="4";
                    ((Main_Activity)getActivity()).binder.sendOrder("ab68"+Address.addr_out+"f006 0202 000" + (isChecked ? "1" : "0"), MainService.SIXSENSOR);
                }});
            switch_b.setOnChangeListener(new Switch.OnSwitchChangedListener(){   //蓝灯开关
                @Override
                public void onSwitchChange(Switch switchView, boolean isChecked) {
                    // TODO Auto-generated method stub
                    which="5";
                    ((Main_Activity)getActivity()).binder.sendOrder("ab68"+Address.addr_out+"f006 0204 000" + (isChecked ? "1" : "0"), MainService.SIXSENSOR);
                }});
            return convertView;
        }
    };

    BaseAdapter adapter_room1=new BaseAdapter() {
        @Override
        public int getCount() {
            // TODO Auto-generated method stub
            return data_room1.size();
        }

        @Override
        public Object getItem(int position) {
            // TODO Auto-generated method stub
            return data_room1.get(position);
        }

        @Override
        public long getItemId(int position) {
            // TODO Auto-generated method stub
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null)
                convertView = LayoutInflater.from(getActivity().getApplication()).inflate(R.layout.parameter_room, null);
            TextView name = (TextView) convertView.findViewById(R.id.name);
            TextView value = (TextView) convertView.findViewById(R.id.value);
            TextView evaluate = (TextView) convertView.findViewById(R.id.evaluate);
            ImageView standard = (ImageView) convertView.findViewById(R.id.standard);   //等级划分条
            ImageView arrow = (ImageView) convertView.findViewById(R.id.arrow);   //箭头
            ImageView img_person = (ImageView) convertView.findViewById(R.id.img_person);   //箭头
            //  LinearLayout layout_number = (LinearLayout) convertView.findViewById((R.id.layout_number));   //显示数字的布局
            LinearLayout layout_arrow = (LinearLayout) convertView.findViewById(R.id.layout_arrow);   //游标的布局
            LinearLayout layout_all = (LinearLayout) convertView.findViewById(R.id.layout_all);   //中间条的整个布局
            LinearLayout layout_person = (LinearLayout) convertView.findViewById(R.id.layout_person);   //显示小人图标的布局
            Map<String, String> map = data_room1.get(position);
            int progress = Integer.valueOf(map.get("Progress"));  //0-100当前值所属的进度条的位置
            name.setText(map.get("Name"));    //参数名称
            value.setText(map.get("Value"));    //参数值，带单位
            //value.setText(map.get("Value")+"+"+ String.valueOf(progress));
            evaluate.setText(map.get("Evaluate"));   //参数值评估结果
            if (map.get("Name").equals("湿度"))  //3个等级
            {
                standard.setImageResource(R.drawable.standard_humidity);
                //   layout_all.setPadding(Pwidth / 10, 0, Pwidth / 10, 0);   //设置颜色条为原长度的3/5
                layout_arrow.setPadding(Pwidth * progress / 200, 0, 0, 0);
                //根据progress大小，<50则设置左边距，>50则设置右边距
                if(progress<50)
                {
                    value.setGravity(Gravity.LEFT);
                    value.setPadding(Pwidth * progress / 200, 0, 0, 0);   //设置左边距
                }
                else
                {
                    value.setGravity(Gravity.RIGHT);
                    value.setPadding(0, 0, Pwidth * (90 - progress) / 200, 0);  //设置右边距
                }
//
            } else if (map.get("Name").equals("光线强度"))  //5个等级
            {
                standard.setImageResource(R.drawable.standard_light);
                //  layout_all.setPadding(Pwidth / 20, 0, Pwidth / 20, 0);   //设置颜色条为原长度的4/5
                layout_arrow.setPadding(Pwidth * progress / 200, 0, 0, 0);
                if(progress<50)
                {
                    value.setGravity(Gravity.LEFT);
                    value.setPadding(Pwidth * progress / 200, 0, 0, 0);   //设置左边距
                }
                else
                {
                    value.setGravity(Gravity.RIGHT);
                    value.setPadding(0, 0, Pwidth * (90 - progress) / 200, 0);  //设置右边距
                }
            } else if (map.get("Name").equals("大气压"))  //3个等级
            {
                standard.setImageResource(R.drawable.standard_humidity);
                layout_arrow.setPadding(Pwidth * progress / 200, 0, 0, 0);
                if(progress<50)
                {
                    value.setGravity(Gravity.LEFT);
                    value.setPadding(Pwidth * progress / 200, 0, 0, 0);   //设置左边距
                }
                else
                {
                    value.setGravity(Gravity.RIGHT);
                    value.setPadding(0, 0, Pwidth * (90 - progress) / 200, 0);  //设置右边距
                }
            } else if (map.get("Name").equals("温度"))  //6个等级
            {
                standard.setImageResource(R.drawable.standard_tem);
                // layout_all.setPadding(0, 0, 0, 0);
                layout_arrow.setPadding(Pwidth * progress / 200, 0, 0, 0);
                if(progress<50)
                {
                    value.setGravity(Gravity.LEFT);
                    value.setPadding(Pwidth * progress / 200, 0, 0, 0);   //设置左边距
                }
                else
                {
                    value.setGravity(Gravity.RIGHT);
                    value.setPadding(0, 0, Pwidth * (90 - progress) / 200, 0);  //设置右边距
                }
            } else if (map.get("Name").equals("人员信息"))  //只显示一个小人的图标
            {
                if (Double.valueOf(map.get("Value")) == 0)   //无人
                {
                    img_person.setImageResource(R.drawable.person0);
                    //layout_arrow.setVisibility(View.GONE);
                } else    //有人
                {
                    img_person.setImageResource(R.drawable.person1);
                    //layout_arrow.setVisibility(View.GONE);
                }
                layout_all.setVisibility(View.GONE);
                layout_person.setVisibility(View.VISIBLE);

            } else   //剩余的，x轴夹角，y轴夹角，水平夹角，振动情况什么的。。
            {
                arrow.setVisibility(View.GONE);
                standard.setVisibility(View.GONE);
            }
            return convertView;
        }
    };

    BaseAdapter adapter_air1=new BaseAdapter() {
        @Override
        public int getCount() {
            // TODO Auto-generated method stub
            return data_air1.size();
        }

        @Override
        public Object getItem(int position) {
            // TODO Auto-generated method stub
            return data_air1.get(position);
        }

        @Override
        public long getItemId(int position) {
            // TODO Auto-generated method stub
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null)
                convertView = LayoutInflater.from(getActivity().getApplication()).inflate(R.layout.parameter_listview, null);
            TextView name = (TextView) convertView.findViewById(R.id.name);
            TextView value = (TextView) convertView.findViewById(R.id.value);
            TextView evaluate = (TextView) convertView.findViewById(R.id.evaluate);
            ImageView standard = (ImageView) convertView.findViewById(R.id.standard);   //等级划分条
            // LinearLayout layout_number = (LinearLayout) convertView.findViewById(R.id.layout_number);// 值
            LinearLayout layout_arrow = (LinearLayout) convertView.findViewById(R.id.layout_arrow);   //游标的布局
            LinearLayout layout_all = (LinearLayout) convertView.findViewById(R.id.layout_all);   //中间条的
            Map<String, String> map = data_air1.get(position);
				/*int width=standard.getWidth();     //标准条的总长度
				int layout_width=layout_all.getWidth();     //区域总长度
*/
            int progress = Integer.valueOf(map.get("Progress"));
            name.setText(map.get("Name"));
            value.setText(map.get("Value"));
            //value.setText(map.get("Value")+"+"+ String.valueOf(progress));
            evaluate.setText(map.get("Evaluate"));
            if (map.get("Name").equals("烟雾"))   //两个等级
            {
                standard.setImageResource(R.drawable.standard_smoke);
                // layout_all.setPadding(Pwidth / 10, 0, Pwidth / 10, 0);   //设置颜色条为原长度的3/5
                layout_arrow.setPadding(Pwidth * progress  / 200, 0, 0, 0);
                if(progress<50)
                {
                    value.setGravity(Gravity.LEFT);
                    value.setPadding(Pwidth * progress / 200, 0, 0, 0);   //设置左边距
                }
                else
                {
                    value.setGravity(Gravity.RIGHT);
                    value.setPadding(0, 0, Pwidth * (90 - progress) / 200, 0);  //设置右边距
                }

            } else if (map.get("Name").equals("甲醛"))  //4个等级
            {
                standard.setImageResource(R.drawable.standard_smoke);
                //layout_all.setPadding(Pwidth / 10, 0, Pwidth / 10, 0);   //设置颜色条为原长度的3/5
                layout_arrow.setPadding(Pwidth * progress  / 200, 0, 0, 0);
                if(progress<50)
                {
                    value.setGravity(Gravity.LEFT);
                    value.setPadding(Pwidth * progress / 200, 0, 0, 0);   //设置左边距
                }
                else
                {
                    value.setGravity(Gravity.RIGHT);
                    value.setPadding(0, 0, Pwidth * (90 - progress) / 200, 0);  //设置右边距
                }
            }//其他都是6个等级
            else {
                layout_arrow.setPadding(Pwidth * progress / 200, 0, 0, 0);
                if(progress<50)
                {
                    value.setGravity(Gravity.LEFT);
                    value.setPadding(Pwidth * progress / 200, 0, 0, 0);   //设置左边距
                }
                else
                {
                    value.setGravity(Gravity.RIGHT);
                    value.setPadding(0, 0, Pwidth * (90 - progress) / 200, 0);  //设置右边距
                }
            }

            return convertView;
        }
    };

    BaseAdapter adapter_posture1=new BaseAdapter() {
        @Override
        public int getCount() {
            // TODO Auto-generated method stub
            return data_posture1.size();
        }

        @Override
        public Object getItem(int position) {
            // TODO Auto-generated method stub
            return data_posture1.get(position);
        }

        @Override
        public long getItemId(int position) {
            // TODO Auto-generated method stub
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null)
                convertView = LayoutInflater.from(getActivity().getApplication()).inflate(R.layout.parameter_posture, null);
            TextView name = (TextView) convertView.findViewById(R.id.name);
            TextView value = (TextView) convertView.findViewById(R.id.value);
            Map<String, String> map = data_posture1.get(position);
            name.setText(map.get("Name"));
            value.setText(map.get("Value"));
            return convertView;
        }
    };

    BaseAdapter adapter_lights1=new BaseAdapter(){
        @Override
        public int getCount() {
            // TODO Auto-generated method stub
            return 1;
        }
        @Override
        public Object getItem(int position) {
            // TODO Auto-generated method stub
            return 1;
        }
        @Override
        public long getItemId(int position) {
            // TODO Auto-generated method stub
            return position;
        }
        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if(convertView==null)
                convertView = LayoutInflater.from(getActivity().getApplication()).inflate(R.layout.parameter_lights, null);
            seekbar_r1=(SeekBar)convertView.findViewById(R.id.seekbar_r);   //红灯滚动条
            seekbar_g1=(SeekBar)convertView.findViewById(R.id.seekbar_g);   //绿灯滚动条
            seekbar_b1=(SeekBar)convertView.findViewById(R.id.seekbar_b);   //蓝灯滚动条
            switch_r1=(Switch)convertView.findViewById(R.id.switch_r);    //红灯开关
            switch_g1=(Switch)convertView.findViewById(R.id.switch_g);    //红灯开关
            switch_b1=(Switch)convertView.findViewById(R.id.switch_b);    //红灯开关
            switch_r1.setState(state_r1==1?true:false);
            switch_g1.setState(state_g1==1?true:false);
            switch_b1.setState(state_b1==1?true:false);
            seekbar_r1.setProgress(progress_r1);
            seekbar_g1.setProgress(progress_g1);
            seekbar_b1.setProgress(progress_b1);
            seekbar_r1.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                //为Seekbar设置状态改变监听
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress,
                                              boolean fromUser) {
                    // TODO Auto-generated method stub
                    progress_r=progress;
                    byte byt=(byte)progress;
                    String str=Converts.Bytes2HexString(new byte[]{byt});
                    ((Main_Activity)getActivity()).binder.sendOrder("ab68"+Address.addr_in+" f006 0201 00" + str, MainService.SIXSENSOR);
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // TODO Auto-generated method stub
                }
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // TODO Auto-generated method stub
                    progress_r=seekBar.getProgress();
                    byte byt=(byte)progress_r;
                    String str=Converts.Bytes2HexString(new byte[]{byt});
                    ((Main_Activity)getActivity()).binder.sendOrder("ab68"+Address.addr_in+" f006 0203 00" + str, MainService.SIXSENSOR);
                }});
            seekbar_g1.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                //为Seekbar设置状态改变监听
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress,
                                              boolean fromUser) {
                    // TODO Auto-generated method stub
                    progress_g=progress;
                    byte byt=(byte)progress;
                    String str=Converts.Bytes2HexString(new byte[]{byt});
                    ((Main_Activity)getActivity()).binder.sendOrder("ab68"+Address.addr_in+"  f006 0203 00" + str, MainService.SIXSENSOR);

                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // TODO Auto-generated method stub
                }
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // TODO Auto-generated method stub
                    progress_g=seekBar.getProgress();
                    byte byt=(byte)progress_g;
                    String str=Converts.Bytes2HexString(new byte[]{byt});
                    ((Main_Activity)getActivity()).binder.sendOrder("ab68"+Address.addr_in+"  f006 0203 00" + str, MainService.SIXSENSOR);
                }});
            seekbar_b1.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                //为Seekbar设置状态改变监听
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress,
                                              boolean fromUser) {
                    // TODO Auto-generated method stub
                    progress_b=progress;
                    byte byt=(byte)progress;
                    String str=Converts.Bytes2HexString(new byte[]{byt});
                    ((Main_Activity)getActivity()).binder.sendOrder("ab68"+Address.addr_in+"  f006 0205 00" + str, MainService.SIXSENSOR);

                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // TODO Auto-generated method stub
                }
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // TODO Auto-generated method stub
                    progress_b=seekBar.getProgress();
                    byte byt=(byte)progress_b;
                    String str=Converts.Bytes2HexString(new byte[]{byt});
                    ((Main_Activity)getActivity()).binder.sendOrder("ab68 "+Address.addr_in+" f006 0203 00" + str, MainService.SIXSENSOR);
                }});
            switch_r1.setOnChangeListener(new Switch.OnSwitchChangedListener(){   //红灯开关
                @Override
                public void onSwitchChange(Switch switchView, boolean isChecked) {
                    // TODO Auto-generated method stub
                    which="3";
                    ((Main_Activity)getActivity()).binder.sendOrder("ab68"+Address.addr_in+"  f006 0200 000" + (isChecked ? "1" : "0"), MainService.SIXSENSOR);

                }});
            switch_g1.setOnChangeListener(new Switch.OnSwitchChangedListener(){   //绿灯开关
                @Override
                public void onSwitchChange(Switch switchView, boolean isChecked) {
                    // TODO Auto-generated method stub
                    which="4";
                    ((Main_Activity)getActivity()).binder.sendOrder("ab68"+Address.addr_in+"  f006 0202 000" + (isChecked ? "1" : "0"), MainService.SIXSENSOR);
                }});
            switch_b1.setOnChangeListener(new Switch.OnSwitchChangedListener(){   //蓝灯开关
                @Override
                public void onSwitchChange(Switch switchView, boolean isChecked) {
                    // TODO Auto-generated method stub
                    which="5";
                    ((Main_Activity)getActivity()).binder.sendOrder("ab68 "+Address.addr_in+" f006 0204 000" + (isChecked ? "1" : "0"), MainService.SIXSENSOR);
                }});
            return convertView;
        }
    };
    //为LishView设置Adapter，用的是SectionListAdapter，分区。参数分为一个区，灯光是一个区
    private void ListInit()
    {
        adapter = new SectionListAdapter(getActivity());  //实例化一个SectionListAdapter
        adapter.addSection("", adapter_time);
        if(((Main_Activity)getActivity()).flag_room.equals("外间")) {
            adapter.addSection("室内环境", adapter_room);
            adapter.addSection("空气质量", adapter_air);
            adapter.addSection("姿态信息", adapter_posture);
            adapter.addSection("灯光控制",adapter_lights);
        }
        else   //里间
        {
            adapter.addSection("室内环境", adapter_room1);
            adapter.addSection("空气质量", adapter_air1);
            adapter.addSection("姿态信息", adapter_posture1);
            adapter.addSection("灯光控制",adapter_lights1);
        }

        list.setAdapter(adapter);

//        list.setAdapter(adapter_time);

    }

}
