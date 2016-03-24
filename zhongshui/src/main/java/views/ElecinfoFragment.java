package views;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.suntrans.zhongshui.Address;
import com.suntrans.zhongshui.Main_Activity;
import com.suntrans.zhongshui.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import convert.Converts;
import database.DbHelper;

/**
 * Created by fendou on 2016/2/28.
 */
public class ElecinfoFragment extends Fragment {
    private byte[] bits={(byte)0x01,(byte)0x02,(byte)0x04,(byte)0x08,(byte)0x10,(byte)0x20,(byte)0x40,(byte)0x80};     //从1到8只有一位是1，用于按位与计算，获取某一位的值
    private PullToRefreshListView mPulltoRefreshListView;   //下拉刷新listview
    private ListView list;
    private int flag_refresh=1;   //是否定时刷新
    private ArrayList<String> rsaddrs=new ArrayList<String>();   //存放所有的开关地址
    private ArrayList<Map<String,String>> data_threephase_1=new ArrayList<>();   //三相电表1的数据   201412000105
    private ArrayList<Map<String,String>> data_threephase_2 = new ArrayList<>();  //三相电表2的数据  201412000099
    private ArrayList<Map<String,String>> data_switchinfo_1=new ArrayList<>();   //开关1的用电参数
    private ArrayList<Map<String,String>> data_switchinfo_2=new ArrayList<>();   //开关2的用电参数
    private ArrayList<Map<String,String>> data_switchinfo_3=new ArrayList<>();   //开关3的用电参数
    private SectionListAdapter adapter;
    public Handler handler1=new Handler(){   //分析返回数据
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
            //   String crc=Converts.GetCRC(a, 2, msg.what-2-2);    //获取返回数据的校验码，倒数第3、4位是验证码，倒数第1、2位是包尾0d0a
            s = s.replace(" ", ""); //去掉空格
            int IsEffective = 0;    //指令是否有效，0表示无效，1表示有效；
            if(s.length()> 14)
            {
                if (s.substring(0, 4).equals("aa69"))  //如果收到的是开关的数据，解析数据，IsEffective=1
                    IsEffective = 1;    //数据有效
                else if(s.substring(0,6).equals("fefe68"))   //如果收到的是电表的数据,IsEffective=2
                    IsEffective = 2;
            }
            if(IsEffective==1)
            {
                final String return_addr=s.substring(4,12);   //返回数据的开关地址
                if (s.substring(12, 14).equals("03"))   //如果是读寄存器状态，解析出开关状态
                {
                    if ((s.substring(14, 16).equals("0e")||s.substring(14,16).equals("07"))&&(a.length>13))   //收到的是开关返回状态的数据，开关状态、电压、电流
                    {
                        double uvalue=(((a[10]+256)%256)*256+(a[11]+256)%256)/10.0;   //电压值
                        double ivalue=(((a[12]+256)%256)*256+(a[13]+256)%256)/10.0;   //电流值
                        double pvalue=uvalue*ivalue;   //功率值
                        //四舍五入，保留两位小数
//                        BigDecimal b   =   new   BigDecimal(pvalue);
//                        pvalue   =   b.setScale(2,   BigDecimal.ROUND_HALF_UP).doubleValue();
                          String pvalue_str;
                        if(pvalue==0.0)
                            pvalue_str="0.00";   //保留两位小数
                        else
                            pvalue_str=new java.text.DecimalFormat("#.00").format(pvalue);   //保留两位小数
                        if(return_addr.equals(Address.addr_out+"0001"))   //第一个开关的数据
                        {
                            data_switchinfo_1.get(0).put("Value", String.valueOf(uvalue) + "  V");
                            data_switchinfo_1.get(1).put("Value", String.valueOf(ivalue) + "  A");
                            data_switchinfo_1.get(2).put("Value", pvalue_str + " W");
//                            adapter_switch1.notifyDataSetChanged();    //刷新相应的数据显示
                        }
                        else if(return_addr.equals(Address.addr_out+"0002"))    //第二个开关的数据
                        {
                            data_switchinfo_2.get(0).put("Value", String.valueOf(uvalue) + "  V");
                            data_switchinfo_2.get(1).put("Value", String.valueOf(ivalue) + "  A");
                            data_switchinfo_2.get(2).put("Value",pvalue_str + " W");
//                            adapter_switch2.notifyDataSetChanged();
                        }
//                        else if(return_addr.equals(Address.addr_in+"0001"))   //第三个开关的数据
//                        {
//                            data_switchinfo_3.get(0).put("Value", String.valueOf(uvalue) + "  V");
//                            data_switchinfo_3.get(1).put("Value", String.valueOf(ivalue) + "  A");
//                            data_switchinfo_3.get(2).put("Value",pvalue_str + " W");
////                            adapter_switch3.notifyDataSetChanged();
//                        }
                       adapter.notifyDataSetChanged();
                    }
                    else if(s.substring(14, 16).equals("10")&&a.length>17)   //过压、欠压和电流等数据
                    {
                        double maxi=(((a[12]+256)%256)*256+(a[13]+256)%256)/1.0;   //最大电流
                        double uv=(((a[14]+256)%256)*256+(a[15]+256)%256)/1.0;   //欠压
                        double ov=(((a[16]+256)%256)*256+(a[17]+256)%256)/1.0;   //过压
                        if(return_addr.equals(Address.addr_out+"0001"))   //第一个开关的数据
                        {
                            data_switchinfo_1.get(3).put("Value", String.valueOf(ov) + "  V");   //过压
                            data_switchinfo_1.get(4).put("Value", String.valueOf(uv) + "  V");    //欠压
                            data_switchinfo_1.get(5).put("Value", String.valueOf(maxi) + "  A");    //最大电流
//                          adapter_switch1.notifyDataSetChanged();
                        }
                        else if(return_addr.equals(Address.addr_out+"0002"))    //第二个开关的数据
                        {
                            data_switchinfo_2.get(3).put("Value", String.valueOf(ov) + "  V");   //过压
                            data_switchinfo_2.get(4).put("Value", String.valueOf(uv) + "  V");    //欠压
                            data_switchinfo_2.get(5).put("Value", String.valueOf(maxi) + "  A");    //最大电流
//                          adapter_switch2.notifyDataSetChanged();

                        }
//                        else if(return_addr.equals(Address.addr_in+"0001"))   //第三个开关的数据
//                        {
//                            data_switchinfo_3.get(3).put("Value", String.valueOf(ov) + "  V");   //过压
//                            data_switchinfo_3.get(4).put("Value", String.valueOf(uv) + "  V");    //欠压
//                            data_switchinfo_3.get(5).put("Value", String.valueOf(maxi) + "  A");    //最大电流
////                          adapter_switch3.notifyDataSetChanged();
//                        }
                        adapter.notifyDataSetChanged();
                    }
                }
            }
            else if(IsEffective==2)   //解析电表数据，表号201407003070为电表1，表号201407001635为电表2
            {
                if(s.substring(6,10).equals("7030")&&msg.what>=28)   //电表1
                {
                    //Log.i("Order", "电表校验结果：" + Converts.getMeterCS(a, 1, a.length - 2)+"真实的："+s.substring(132,134));
                   try {
                       double VA = (Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[13] & 0xff) - 51)})) * 100 + Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[12] & 0xff) - 51)}))) / 10.0;  //A相电压。单位是V
                       double IA = (Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[16] & 0xff) - 51)})) * 10000 + Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[15] & 0xff) - 51)})) * 100 + Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[14] & 0xff) - 51)}))) / 1000.0;  //A相电流，单位是A
                       double Active_Power = (Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[19] & 0xff) - 51)})) * 10000 + Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[18] & 0xff) - 51)})) * 100 + Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[17] & 0xff) - 51)}))) / 10000.0;  //有功功率，单位是kW
                       double Powerrate = (Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[21] & 0xff) - 51)})) * 100 + Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[20] & 0xff) - 51)}))) / 1000.0;  //功率因数
                       double Electricity = (Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[25] & 0xff) - 51)})) * 10000 + Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[24] & 0xff) - 51)})) * 100 + Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[23] & 0xff) - 51)}))) + Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[22] & 0xff) - 51)})) / 100.0;  //总用电量，单位是kWh
                       data_threephase_1.get(0).put("Value", String.valueOf(Electricity) + " kWh");  //总用电量
                       data_threephase_1.get(1).put("Value", String.valueOf(VA) + " V");   //电压
                       data_threephase_1.get(2).put("Value", String.valueOf(IA) + " A");   //电流
                       data_threephase_1.get(3).put("Value", String.valueOf(Active_Power) + " kW");   //总有功功率
                       data_threephase_1.get(4).put("Value", String.valueOf(Powerrate) + " ");   //功率因数
                   }
                   catch(Exception e){e.printStackTrace();}
                  //  adapter_threephase1.notifyDataSetChanged();
                    adapter.notifyDataSetChanged();
                }
                else if(s.substring(6,10).equals("3516")&&msg.what>=28)  //电表2
                {
                    try {
                        double VA = (Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[13] & 0xff) - 51)})) * 100 + Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[12] & 0xff) - 51)}))) / 10.0;  //A相电压。单位是V
                        double IA = (Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[16] & 0xff) - 51)})) * 10000 + Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[15] & 0xff) - 51)})) * 100 + Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[14] & 0xff) - 51)}))) / 1000.0;  //A相电流，单位是A
                        double Active_Power = (Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[19] & 0xff) - 51)})) * 10000 + Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[18] & 0xff) - 51)})) * 100 + Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[17] & 0xff) - 51)}))) / 10000.0;  //有功功率，单位是kW
                        double Powerrate = (Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[21] & 0xff) - 51)})) * 100 + Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[20] & 0xff) - 51)}))) / 1000.0;  //功率因数
                        double Electricity = (Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[25] & 0xff) - 51)})) * 10000 + Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[24] & 0xff) - 51)})) * 100 + Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[23] & 0xff) - 51)}))) + Integer.valueOf(Converts.Bytes2HexString(new byte[]{(byte) ((a[22] & 0xff) - 51)})) / 100.0;  //总用电量，单位是kWh
                        data_threephase_2.get(0).put("Value", String.valueOf(Electricity) + " kWh");  //总用电量
                        data_threephase_2.get(1).put("Value", String.valueOf(VA) + " V");   //电压
                        data_threephase_2.get(2).put("Value", String.valueOf(IA) + " A");   //电流
                        data_threephase_2.get(3).put("Value", String.valueOf(Active_Power) + " kW");   //总有功功率
                        data_threephase_2.get(4).put("Value", String.valueOf(Powerrate) + " ");   //功率因数
                    }catch(Exception e) {
                        e.printStackTrace();}

                       adapter.notifyDataSetChanged();
//                    adapter_threephase1.notifyDataSetChanged();
                    }
            }


        }
    };
    @Override     //当前页面可见与不可见的状态
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser)
        {    //可见时
            try
            {
                mPulltoRefreshListView.setRefreshing();
                flag_refresh=1;
                new RefreshThread().start();
            }
            catch (Exception e)
            {

            }
            //相当于Fragment的onResume
        }
        else
            flag_refresh=0;   //不可见时关闭自动刷新
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.i("Order", "control==>onCreateView");
        View view = inflater.inflate(R.layout.elecinfo, null);
        mPulltoRefreshListView = (PullToRefreshListView) view.findViewById(R.id.list);
        list=mPulltoRefreshListView.getRefreshableView();
        mPulltoRefreshListView.setMode(PullToRefreshBase.Mode.PULL_FROM_START);  //设置模式为只有下拉
        mPulltoRefreshListView.setOnRefreshListener(new PullToRefreshBase.OnRefreshListener<ListView>() {
            @Override
            public void onRefresh(PullToRefreshBase<ListView> refreshView) {
                String label = DateUtils.formatDateTime(getActivity().getApplicationContext(), System.currentTimeMillis(),
                        DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_ALL);
                // Update the LastUpdatedLabel
                refreshView.getLoadingLayoutProxy().setLastUpdatedLabel("上次刷新：" + label);
                new GetDataTask().execute();   //执行刷新任务
            }
        });
        DataInit();
        ListInit();
        return view;
    }
    @Override
    public void onDestroy(){
        super.onDestroy();
        flag_refresh=0;
    }

    //新建刷新线程
    class RefreshThread extends Thread{
        @Override
        public void run(){
            while(flag_refresh==1)
            {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                String order = "fe68 703000071420 681f00";
                ((Main_Activity) getActivity()).binder.sendOrder(order, 3);   //发送命令，读取三相电表状态
                try {
                    Thread.sleep(300);  //为避免数据错乱，适当延时
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                order = "fe68 351600071420 681f00";
                ((Main_Activity) getActivity()).binder.sendOrder(order, 3);   //发送命令，读取三相电表状态
                try {
                    Thread.sleep(300);  //为避免数据错乱，适当延时
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                for (String switch_addr : rsaddrs) {
                    order = "aa68" + switch_addr + "03 0200 0008";
                    ((Main_Activity) getActivity()).binder.sendOrder(order, 2);   //发送命令，读取过压、欠压、最大电流
                    try {
                        Thread.sleep(300);  //为避免数据错乱，适当延时
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    Thread.sleep(5000);   //自动刷新间隔为5s
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    ///下拉刷新处理的函数。
    private class GetDataTask extends AsyncTask<Void, Void, String>
    {
        // 后台处理部分
        @Override
        protected String doInBackground(Void... params) {
            //String order = "fe68 aaaaaaaaaaaa 681f00";
            String order = "fe68 990000121420 681f00";
            ((Main_Activity) getActivity()).binder.sendOrder(order, 3);   //发送命令，读取三相电表状态
            try {
                Thread.sleep(100);  //为避免数据错乱，适当延时
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            order = "fe68 050100121420 681f00";
            ((Main_Activity) getActivity()).binder.sendOrder(order, 3);   //发送命令，读取三相电表状态
            try {
                Thread.sleep(100);  //为避免数据错乱，适当延时
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            for (String switch_addr : rsaddrs) {
                order = "aa68" + switch_addr + "03 0100 0007";
                ((Main_Activity) getActivity()).binder.sendOrder(order, 2);   //发送命令，读取电压、电流
                try {
                    Thread.sleep(100);  //为避免数据错乱，适当延时
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            for (String switch_addr : rsaddrs) {
                order = "aa68" + switch_addr + "03 0200 0008";
                ((Main_Activity) getActivity()).binder.sendOrder(order, 2);   //发送命令，读取过压、欠压、最大电流
                try {
                    Thread.sleep(100);  //为避免数据错乱，适当延时
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
            //     String order="ab68"+addr+"f003 0100 0011";
            //   ((Main_Activity) getActivity()).binder.sendOrder(order,4);

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
            mPulltoRefreshListView.onRefreshComplete();   //表示刷新完成

            super.onPostExecute(result);//这句是必有的，AsyncTask规定的格式
        }
    }

    private void DataInit() {
        //第一个三相电表的数据
        Map<String, String> map0 = new HashMap<>();
        map0.put("Name", "总用电量");
        map0.put("Value", "null");
        data_threephase_1.add(map0);

        Map<String, String> map1 = new HashMap<>();
        map1.put("Name", "电压");
        map1.put("Value", "null");
        data_threephase_1.add(map1);

        Map<String, String> map4 = new HashMap<>();
        map4.put("Name", "电流");
        map4.put("Value", "null");
        data_threephase_1.add(map4);

        Map<String, String> map7 = new HashMap<>();
        map7.put("Name", "有功功率");
        map7.put("Value", "null");
        data_threephase_1.add(map7);

        Map<String, String> map24 = new HashMap<>();
        map24.put("Name", "功率因数");
        map24.put("Value", "null");
        data_threephase_1.add(map24);

        //第二个三相电表的数据
        Map<String, String> map30 = new HashMap<>();
        map30.put("Name", "总用电量");
        map30.put("Value", "null");
        data_threephase_2.add(map30);

        Map<String, String> map33 = new HashMap<>();
        map33.put("Name", "电压");
        map33.put("Value", "null");
        data_threephase_2.add(map33);

        Map<String, String> map36 = new HashMap<>();
        map36.put("Name", "电流");
        map36.put("Value", "null");
        data_threephase_2.add(map36);

        Map<String, String> map37 = new HashMap<>();
        map37.put("Name", "有功功率");
        map37.put("Value", "null");
        data_threephase_2.add(map37);

        Map<String, String> map39 = new HashMap<>();
        map39.put("Name", "功率因数");
        map39.put("Value", "null");
        data_threephase_2.add(map39);

        //第一个开关的数据
        Map<String, String> map9 = new HashMap<>();
        map9.put("Name", "电压");
        map9.put("Value", "null");
        data_switchinfo_1.add(map9);

        Map<String, String> map10 = new HashMap<>();
        map10.put("Name", "电流");
        map10.put("Value", "null");
        data_switchinfo_1.add(map10);

        Map<String, String> map25 = new HashMap<>();
        map25.put("Name", "功率");
        map25.put("Value", "null");
        data_switchinfo_1.add(map25);

        Map<String, String> map11 = new HashMap<>();
        map11.put("Name", "过压");
        map11.put("Value", "null");
        data_switchinfo_1.add(map11);

        Map<String, String> map12 = new HashMap<>();
        map12.put("Name", "欠压");
        map12.put("Value", "null");
        data_switchinfo_1.add(map12);

        Map<String, String> map13 = new HashMap<>();
        map13.put("Name", "最大电流");
        map13.put("Value", "null");
        data_switchinfo_1.add(map13);

        //第二个开关的数据
        Map<String, String> map14 = new HashMap<>();
        map14.put("Name", "电压");
        map14.put("Value", "null");
        data_switchinfo_2.add(map14);

        Map<String, String> map15 = new HashMap<>();
        map15.put("Name", "电流");
        map15.put("Value", "null");
        data_switchinfo_2.add(map15);

        Map<String, String> map26 = new HashMap<>();
        map26.put("Name", "功率");
        map26.put("Value", "null");
        data_switchinfo_2.add(map26);

        Map<String, String> map16 = new HashMap<>();
        map16.put("Name", "过压");
        map16.put("Value", "null");
        data_switchinfo_2.add(map16);

        Map<String, String> map17 = new HashMap<>();
        map17.put("Name", "欠压");
        map17.put("Value", "null");
        data_switchinfo_2.add(map17);

        Map<String, String> map18 = new HashMap<>();
        map18.put("Name", "最大电流");
        map18.put("Value", "null");
        data_switchinfo_2.add(map18);


        DbHelper dh1 = new DbHelper(getActivity(), "IBMS", null, 1);
        SQLiteDatabase db = dh1.getWritableDatabase();
        //外间
        Cursor cursor = db.query(true, "switchs_tb", new String[]{"RSAddr", "MainAddr"}, null, null, null, null, null, null);
        while (cursor.moveToNext())
            rsaddrs.add(cursor.getString(1) + cursor.getString(0));   //外间开关485地址

        cursor.close();

        db.close();
    }

    //三相电表数据部分adapter
    BaseAdapter adapter_threephase1=new BaseAdapter() {
        @Override
        public int getCount() {
            return data_threephase_1.size();
        }

        @Override
        public Object getItem(int position) {
            return data_threephase_1.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null)
                convertView = LayoutInflater.from(getActivity().getApplication()).inflate(R.layout.elecinfo_listview, null);
            ImageView image = (ImageView) convertView.findViewById(R.id.image);
            TextView name = (TextView) convertView.findViewById(R.id.name);
            TextView value = (TextView) convertView.findViewById(R.id.value);
            Map<String, String> map = data_threephase_1.get(position);
            name.setText(map.get("Name"));
            value.setText(map.get("Value"));
            Bitmap bmp;
            if(position==0)   //用电量图片
            {
                bmp = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.ic_elec);
                image.setImageBitmap(bmp);
            }
            else if(position>=1&&position<=3)   //电压
            {
                bmp = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.ic_voltage);
                image.setImageBitmap(bmp);
            }
            else if(position>=4&&position<=6)    //电流
            {
                bmp = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.ic_current);
                image.setImageBitmap(bmp);
            }
            else if(position>=7&&position<=8)
            {
                bmp = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.ic_power);
                image.setImageBitmap(bmp);

            }
            else
            {
                bmp = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.ic_powerrate);
                image.setImageBitmap(bmp);

            }
            return convertView;
        }
    };

    BaseAdapter adapter_threephase2=new BaseAdapter() {
        @Override
        public int getCount() {
            return data_threephase_2.size();
        }

        @Override
        public Object getItem(int position) {
            return data_threephase_2.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null)
                convertView = LayoutInflater.from(getActivity().getApplication()).inflate(R.layout.elecinfo_listview, null);
            ImageView image = (ImageView) convertView.findViewById(R.id.image);
            TextView name = (TextView) convertView.findViewById(R.id.name);
            TextView value = (TextView) convertView.findViewById(R.id.value);
            Map<String, String> map = data_threephase_2.get(position);
            name.setText(map.get("Name"));
            value.setText(map.get("Value"));
            Bitmap bmp;
            if(position==0)   //用电量图片
            {
                bmp = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.ic_elec);
                image.setImageBitmap(bmp);
            }
            else if(position>=1&&position<=3)   //电压
            {
                bmp = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.ic_voltage);
                image.setImageBitmap(bmp);
            }
            else if(position>=4&&position<=6)    //电流
            {
                bmp = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.ic_current);
                image.setImageBitmap(bmp);
            }
            else if(position>=7&&position<=8)
            {
                bmp = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.ic_power);
                image.setImageBitmap(bmp);
            }
            else
            {
                bmp = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.ic_powerrate);
                image.setImageBitmap(bmp);
            }
            return convertView;
        }
    };

    BaseAdapter adapter_switch1= new BaseAdapter() {
        @Override
        public int getCount() {
            return data_switchinfo_1.size();
        }

        @Override
        public Object getItem(int position) {
            return data_switchinfo_1.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null)
                convertView = LayoutInflater.from(getActivity().getApplication()).inflate(R.layout.elecinfo_listview, null);
            TextView name = (TextView) convertView.findViewById(R.id.name);
            TextView value = (TextView) convertView.findViewById(R.id.value);
            ImageView image = (ImageView) convertView.findViewById(R.id.image);
            Map<String, String> map = data_switchinfo_1.get(position);
            name.setText(map.get("Name"));
            value.setText(map.get("Value"));
            Bitmap bmp;
            if(position==0||position==3||position==4)   //电压
            {
                bmp = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.ic_voltage);
                image.setImageBitmap(bmp);
            }
            else if(position==2)  //功率
            {
                bmp = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.ic_power);
                image.setImageBitmap(bmp);
            }
            else
            {
                bmp = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.ic_current);
                image.setImageBitmap(bmp);
            }
            return convertView;
        }
    };

    BaseAdapter adapter_switch2=new BaseAdapter() {
        @Override
        public int getCount() {
            return data_switchinfo_2.size();
        }

        @Override
        public Object getItem(int position) {
            return data_switchinfo_2.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null)
                convertView = LayoutInflater.from(getActivity().getApplication()).inflate(R.layout.elecinfo_listview, null);
            TextView name = (TextView) convertView.findViewById(R.id.name);
            TextView value = (TextView) convertView.findViewById(R.id.value);
            ImageView image = (ImageView) convertView.findViewById(R.id.image);
            Map<String, String> map = data_switchinfo_2.get(position);
            name.setText(map.get("Name"));
            value.setText(map.get("Value"));
            Bitmap bmp;
            if(position==0||position==3||position==4)   //电压
            {
                bmp = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.ic_voltage);
                image.setImageBitmap(bmp);
            }
            else if(position==2)  //功率
            {
                bmp = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.ic_power);
                image.setImageBitmap(bmp);
            }
            else
            {
                bmp = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.ic_current);
                image.setImageBitmap(bmp);
            }
            return convertView;
        }
    };

    BaseAdapter adapter_switch3=new BaseAdapter() {
        @Override
        public int getCount() {
            return data_switchinfo_3.size();
        }

        @Override
        public Object getItem(int position) {
            return data_switchinfo_3.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null)
                convertView = LayoutInflater.from(getActivity().getApplication()).inflate(R.layout.elecinfo_listview, null);
            TextView name = (TextView) convertView.findViewById(R.id.name);
            TextView value = (TextView) convertView.findViewById(R.id.value);
            ImageView image= (ImageView) convertView.findViewById(R.id.image);
            Map<String, String> map = data_switchinfo_3.get(position);
            name.setText(map.get("Name"));
            value.setText(map.get("Value"));
            Bitmap bmp;
            if(position==0||position==3||position==4)   //电压
            {
                bmp = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.ic_voltage);
                image.setImageBitmap(bmp);
            }
            else if(position==2)  //功率
            {
                bmp = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.ic_power);
                image.setImageBitmap(bmp);
            }
            else
            {
                bmp = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.ic_current);
                image.setImageBitmap(bmp);
            }
            return convertView;
        }
    };

    private void ListInit(){
        adapter = new SectionListAdapter(getActivity());  //实例化一个SectionListAdapter
        //三相电表1数据
        adapter.addSection("单相电表1", adapter_threephase1);
        //三相电表2数据
        adapter.addSection("单相电表2", adapter_threephase2);
        //开关1
        adapter.addSection("开关1", adapter_switch1);
        //开关2
        adapter.addSection("开关2", adapter_switch2);
        //开关3
      //  adapter.addSection("开关3", adapter_switch3);

        list.setAdapter(adapter);
    }
}
