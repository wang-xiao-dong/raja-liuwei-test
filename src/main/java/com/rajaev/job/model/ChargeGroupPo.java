package com.rajaev.job.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Created by WXD on 2019/1/11.
 * 百度地图充电站详情
 */
@Data
public class ChargeGroupPo {
    String chargeId;
    //充电站名称
    String name;
    //充电站地址
    String address;
    //充电站功率（KW）
    String power;
    //电费（元/度）
    String chargingFee;
    //服务费（元/度）
    String serviceFee;
    //停车费
    String parkingFee;
    //充电总费用(电费+服务费)（元/度）
    String totalFee;
    //支付方式
    String payment;
    //空闲直流充电桩个数
    Integer dcLeft;
    //总直流充电桩个数
    Integer dcTotal;
    //空闲交流充电桩个数
    Integer acLeft;
    //总交流充电桩个数
    Integer acTotal;
    //百度经度
    String lng;
    //百度纬度
    String lat;
    //百度经度转换为bd09mc值(百度米制经度坐标)
    String pointX;
    //百度纬度转换为bd09mc值(百度米制纬度坐标)
    String pointY;
    //运营时间,格式为："00:00-24:00"
    String busineHours;
    //是否对外开放,1对外开放，0不对外开放
    Integer isOpen;
}
