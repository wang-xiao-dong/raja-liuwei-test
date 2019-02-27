package com.rajaev.job;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import com.rajaev.job.model.ChargeGroupPo;
import com.rajaev.job.model.CityPo;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Resource;
import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by WXD on 2019/1/7.
 * 方式一：思路：模拟软件操作流程，正向抓取，先按城市取出充电站id，再按id取充电站详情
 *    缺点：不太好控制屏幕移动次数，
 *     要想屏幕移动几次后完全包含所在城市，一个城市需要多次移动调用接口，城市之间区域的重复抓取耗费时间
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class RelationGroupJobTest2 {
    @Autowired
    RestTemplate restTemplate;
    @Autowired
    RedisTemplate redisTemplate;

    //每次设置的屏幕距离大小，距离中心位置的偏移量
    private  static Float relativeX =  40000F;
    private  static Float relativeY =  80000F;
    private NumberFormat floatMatter = new DecimalFormat("0.0000");


    //①根据每个城市的pointX和pointY坐标，查询出每个城市周边的充电站id,存入Redis
    //其中，各个城市的信息，已经抓取保存进Redis的city_info中，城市信息不会变，可认为是死数据，不去修改
    @Test
    public void saveGroupId() throws IOException {
        List<CityPo> reslutMapList= redisTemplate.opsForHash().values("city_info");
        Map<String,List<String>> groupIds = Maps.newHashMap();
        for(CityPo city : reslutMapList) {
            List<String> chargeIds = Lists.newArrayList();
            String pointX = city.getPointX();
            String pointY = city.getPointY();
            Map<String,Object> chargeInfos0 = getChargeIdInfos(pointX,pointY);
            chargeIds.addAll((Set<String>)chargeInfos0.get("chargeIds"));
            System.err.println("首次查询个数："+((Set<String>) chargeInfos0.get("chargeIds")).size());
            //将第一次设置的屏幕4个角的坐标分别再设置为屏幕中心点，按相同的偏移量再次查询出相关区域内的所有充电站
            //弥补模拟手机屏幕，一次查询不全相应城市的所有充电站问题
            //将第一次屏幕的坐上角坐标设置为屏幕中心点
            String x2= floatMatter.format(Float.valueOf(pointX)-relativeX);
            String y2= floatMatter.format(Float.valueOf(pointY)+relativeY);
            Map<String,Object> chargeInfos1 = getChargeIdInfos(x2,y2);
            chargeIds.addAll((Set<String>)chargeInfos1.get("chargeIds"));
            System.err.println("左上角查询个数："+((Set<String>) chargeInfos1.get("chargeIds")).size());
            //将第一次屏幕的坐下角坐标设置为屏幕中心点
            String x3= floatMatter.format(Float.valueOf(pointX)-relativeX);
            String y3= floatMatter.format(Float.valueOf(pointY)-relativeY);
            Map<String,Object> chargeInfos2 = getChargeIdInfos(x3,y3);
            chargeIds.addAll((Set<String>)chargeInfos2.get("chargeIds"));
            System.err.println("左下角查询个数："+((Set<String>) chargeInfos2.get("chargeIds")).size());
            //将第一次屏幕的右上角坐标设置为屏幕中心点
            String x4= floatMatter.format(Float.valueOf(pointX)+relativeX);
            String y4= floatMatter.format(Float.valueOf(pointY)+relativeY);
            Map<String,Object> chargeInfos3 = getChargeIdInfos(x4,y4);
            chargeIds.addAll((Set<String>)chargeInfos3.get("chargeIds"));
            System.err.println("右上角查询个数："+((Set<String>) chargeInfos3.get("chargeIds")).size());
            //将第一次屏幕的右下角坐标设置为屏幕中心点
            String x5= floatMatter.format(Float.valueOf(pointX)+relativeX);
            String y5= floatMatter.format(Float.valueOf(pointY)-relativeY);
            Map<String,Object> chargeInfos4 = getChargeIdInfos(x5,y5);
            chargeIds.addAll((Set<String>)chargeInfos4.get("chargeIds"));
            System.err.println("右下角查询个数："+((Set<String>) chargeInfos4.get("chargeIds")).size());
            System.err.println("城市"+city.getCityName()+"的查询个数："+chargeIds.size());
            //保存进Redis
            //key为城市的adCode，value为充电站id
            groupIds.put(city.getAdCode(),chargeIds);
        }
        redisTemplate.opsForHash().putAll("city_group_id",groupIds);
    }

    //②根据充电站id，获取充电站详情，并保存进Redis
    @Test
    public void saveGroupDetail() {
        //读出所有充电站id
        List<List<String>> chargeIdListTemp= redisTemplate.opsForHash().values("city_group_id");
        List<String> allTemp = Lists.newArrayList();
        for(List<String> x : chargeIdListTemp) {
            allTemp.addAll(x);
        }
        //去重
        List<String> chargeIdList = Lists.newArrayList(Sets.newHashSet(allTemp));
        //排序
        chargeIdList = new Ordering<String>() {
            @Override
            public int compare(String left, String right) {
                return Long.valueOf(left).compareTo(Long.valueOf(right));
            }
        }.immutableSortedCopy(chargeIdList);
        //读出的最大充电站id为47995，最小为1，说明百度是取的自增id，但chargeIdList只有1万多条，
        // 说明中间有很多遗漏，需要调整saveGroupId的算法。
        System.err.println(chargeIdList.get(chargeIdList.size()-1)+"===="+chargeIdList.get(0));


        String urlFormat = "https://oil.baidu.com/chargemap/station/info?chargeId=%s&sign=612d921d043b699d508f3f84bfeb3186&lat=9569020000&lng=91559991000";
        Map<String,ChargeGroupPo> detailMap = redisTemplate.opsForHash().entries("city_group_detail");
        if(null == detailMap){
            detailMap = Maps.newHashMap();
        }
        //此处优化可开多线程将chargeIdList分组查询
        for (String id : chargeIdList){
            String url = String.format(urlFormat,Integer.valueOf(id));
            ResponseEntity<String> responseEntity = restTemplate.getForEntity(url, String.class);
            String body = responseEntity.getBody();
            Map mapType = JSON.parseObject(body, Map.class);
            if(mapType.get("data").toString().length() > 2){
                ChargeGroupPo chargeGroupPo = new ChargeGroupPo();
                JSONObject jSONObject = JSON.parseObject(mapType.get("data").toString());
                String chargeId = URLDecoder.decode(jSONObject.get("chargeId").toString());
                chargeGroupPo.setChargeId(chargeId);
                chargeGroupPo.setName(URLDecoder.decode(jSONObject.get("name").toString()));
                chargeGroupPo.setAddress(URLDecoder.decode(jSONObject.get("address").toString()));
                String feeStr = URLDecoder.decode(jSONObject.get("chargingFee").toString());
                chargeGroupPo.setChargingFee(feeStr);
                feeStr = URLDecoder.decode(jSONObject.get("serviceFee").toString());
                chargeGroupPo.setServiceFee(feeStr);
                feeStr = URLDecoder.decode(jSONObject.get("parkingFee").toString());
                chargeGroupPo.setParkingFee(feeStr);
                feeStr = URLDecoder.decode(jSONObject.get("totalFee").toString());
                chargeGroupPo.setTotalFee(feeStr);
                String payStr = URLDecoder.decode(jSONObject.get("payment").toString());
                chargeGroupPo.setPayment(StringUtils.substringBefore(payStr,","));
                chargeGroupPo.setPower(URLDecoder.decode(jSONObject.get("power").toString()));
                chargeGroupPo.setLng(URLDecoder.decode(jSONObject.get("lng").toString()));
                chargeGroupPo.setLat(URLDecoder.decode(jSONObject.get("lat").toString()));
                chargeGroupPo.setBusineHours(URLDecoder.decode(jSONObject.get("busineHours").toString()));
                chargeGroupPo.setIsOpen(Integer.valueOf(URLDecoder.decode(jSONObject.get("isOpen").toString())));
                chargeGroupPo.setDcTotal(Integer.valueOf(URLDecoder.decode(jSONObject.get("dcTotal").toString())));
                chargeGroupPo.setDcLeft(Integer.valueOf(URLDecoder.decode(jSONObject.get("dcTotal").toString())));
                chargeGroupPo.setAcTotal(Integer.valueOf(URLDecoder.decode(jSONObject.get("acTotal").toString())));
                chargeGroupPo.setAcLeft(Integer.valueOf(URLDecoder.decode(jSONObject.get("acLeft").toString())));
                chargeGroupPo.setPointX(URLDecoder.decode(jSONObject.get("pointX").toString()));
                chargeGroupPo.setPointY(URLDecoder.decode(jSONObject.get("pointY").toString()));
                detailMap.put(chargeId,chargeGroupPo);
            }
        }
        redisTemplate.opsForHash().putAll("city_group_detail",detailMap);
    }


    public Map<String,Object> getChargeIdInfos(String pointX,String pointY) throws IOException {
//        其中：url中的pointX，pointY代表当前定位点，屏幕中心点
////		format中sw中的坐标代表，手机屏幕左下角坐标，ne代表右上角坐标，可调整二者的值，模拟手机屏幕显示区域的大小
////		scale代表手机地图的缩放比例
        String url = "https://oil.baidu.com/chargemap/station/list?isFirst=0&scale=14&pointX="+pointX+"&pointY="+pointY+"&sign=612d921d043b699d508f3f84bfeb3186&ufrom=yes&loc=undefined&lng="+pointX+"&lat="+pointY+"&t=1544719918713";
        //String url = String.format(urlFormat,pointX,pointY,pointX,pointY);
        String minX1 = floatMatter.format(Float.valueOf(pointX) - relativeX);
        String minY1 = floatMatter.format(Float.valueOf(pointY) - relativeY);
        String maxX1 = floatMatter.format(Float.valueOf(pointX) + relativeX);
        String maxY1 = floatMatter.format(Float.valueOf(pointY) + relativeY);
        String format = "&bounds={\"sw\":{\"lng\":"+minX1+",\"lat\":"+minY1+"},\"ne\":{\"lng\":"+
                maxX1+",\"lat\":"+maxY1+"}}";
        url = url + format;
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
        URI uri = builder.build().encode().toUri();
        ResponseEntity<String> responseEntity = restTemplate.getForEntity(uri, String.class);
        String body = responseEntity.getBody();
        //从返回的参数中获取chargeIds，和相应充电站最大的xy坐标
        Map mapType = JSON.parseObject(body, Map.class);
        JSONObject jSONObject = JSON.parseObject(mapType.get("data").toString());
        JSONArray chargeJsonArray = JSON.parseArray(jSONObject.get("chargeList").toString());
        Set<String> chargeIds = Sets.newHashSet();
        for (int i = 0; i < chargeJsonArray.size(); i++) {
            JSONObject chargeJsonObject = chargeJsonArray.getJSONObject(i);
            JSONObject chargeInfoJsonObject = JSON.parseObject(chargeJsonObject.get("centerPoint").toString());
            chargeIds.add(chargeInfoJsonObject.get("chargeId").toString());
        }
        Map<String,Object> returnMap = Maps.newHashMap();
        returnMap.put("chargeIds",chargeIds);
        return returnMap;
    }

}
