package com.suntrans.zhongshui;

/**
 * Created by 1111b on 2016/1/22.
 */
public class SwitchState {
    private static String[] state1 = new String[]{"0","0","0","0","0","0","0","0","0","0","0"};    //外间的开关状态,依次是总开关，通道1，通道2，通道3，通道4，。。。，通道10， 下同
    private static String[] state2 = new String[]{"0","0","0","0","0","0","0","0","0","0","0"};    //里间的开关状态
    private static byte[] bits={(byte)0x01,(byte)0x02,(byte)0x04,(byte)0x08,(byte)0x10,(byte)0x20,(byte)0x40,(byte)0x80};     //从1到8只有一位是1，用于按位与计算，获取某一位的值
    /*
    更新开关状态
    @data，命令十六进制数组
    @count，命令长度，字节数
     */
    public static void setState(byte[] data,int count)   //根据命令更改开关状态
    {
        String s="";
        for (int i = 0; i < count; i++) {
            String s1 = Integer.toHexString((data[i] + 256) % 256);   //byte转换成十六进制字符串(先把byte转换成0-255之间的非负数，因为java中的数据都是带符号的)
            if (s1.length() == 1)
                s1 = "0" + s1;
            s = s + s1;
        }
        //   String crc=Converts.GetCRC(a, 2, msg.what-2-2);    //获取返回数据的校验码，倒数第3、4位是验证码，倒数第1、2位是包尾0d0a
        s = s.replace(" ", ""); //去掉空格
        int IsEffective = 0;    //指令是否有效，0表示无效，1表示有效；对于和第六感官通讯而言，包头为ab68的数据才有效
        if (count > 13) {
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
                  //  String[] states={"0","0","0","0","0","0","0","0","0","0"};   //十个通道的状态，state[0]对应1通道
                    for(int i=0;i<8;i++)   //先获取前八位的开关状态
                    {
                        state1[i+1]=((data[8]&bits[i])==bits[i])?"1":"0";   //1-8通道
                    }
                    for(int i=0;i<2;i++)
                    {
                        state1[i+9]=((data[7]&bits[i])==bits[i])?"1":"0";  //9、10通道
                    }
                    state1[0]=((data[7]&bits[2])==bits[2])?"1":"0";
//                    for(int i=0;i<10;i++)
//                        data.get(i).put("State", states[Integer.valueOf(data.get(i).get("Channel").toString().equals("a")?"10":data.get(i).get("Channel").toString())-1]);


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
                    state1[0]=String.valueOf(state); //更新总开关数组中的开关状态
                    if(state==0)   //如果总开关关了，那肯定所有通道都关了
                    {
                        for(int i=0;i<11;i++)
                            state1[i] = "0";
                    }
                }
                else     //如果通道号不为0，则更改data中的状态，并更新
                {
                    state1[k] = String.valueOf(state);
                }
            }
        }

        else if(IsEffective==2)   //里间的开关数据
        {
            if (s.substring(10, 12).equals("03"))   //如果是读寄存器状态，解析出开关状态
            {
                if (s.substring(12, 14).equals("0e")||s.substring(12,14).equals("07"))
                {
                   // String[] states={"0","0","0","0","0","0","0","0","0","0"};   //十个通道的状态，state[0]对应1通道
                    for(int i=0;i<8;i++)   //先获取前八位的开关状态
                    {
                        state2[i+1]=((data[8]&bits[i])==bits[i])?"1":"0";   //1-8通道
                    }
                    for(int i=0;i<2;i++)
                    {
                        state2[i+9]=((data[7]&bits[i])==bits[i])?"1":"0";  //9、10通道
                    }
                    state2[0]=((data[7]&bits[2])==bits[2])?"1":"0";

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
                    state2[0]=String.valueOf(state); //更新总开关数组中的开关状态
                    if(state==0)   //如果总开关关了，那肯定所有通道都关了
                    {
                        for(int i=0;i<11;i++)
                            state2[i]="0";
                    }
                }
                else     //如果通道号不为0，则更改data中的状态，并更新
                {
                    state2[k] = String.valueOf(state);
                }

            }
        }
    }

    /*
    获取房间的开关状态
    @addr，房间开关地址
     */
    public static String[] getSate(String addr)
    {
        if(addr.equals(Address.addr_out))
            return state1;   //外间开关状态
        else
            return state2;   //里间开关状态
    }

}
