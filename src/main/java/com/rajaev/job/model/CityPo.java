package com.rajaev.job.model;

import lombok.Data;

import java.io.Serializable;


/**
 * Created by WXD on 2019/1/11.
 * 百度对应的城市信息
 */
@Data
public class CityPo implements Serializable {
    //城市名称
    String cityName;
    //城市编码(最小到市,百度已不推荐使用,但很多接口任需要此参数)
    String cityCode;
    //地址编码（最小到区县，百度推荐使用）
    String adCode;
    //城市中心的经度
    String lng;
    //城市中心的纬度
    String lat;
    //百度经度转换为bd09mc值(百度米制经度坐标)
    String pointX;
    //百度纬度转换为bd09mc值(百度米制纬度坐标)
    String pointY;
}
