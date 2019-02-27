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
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Resource;
import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by WXD on 2019/1/7.
 * 思路二：根据接口返回数据，分析网站代码结构
 *     根据上面的方法1读取出的充电站id信息结构
 *     最大充电站id为47995，最小为1，说明百度取的是自增id，可以用穷举法，调用接口，
 *     先取出所有的充电站详情，再根据详情按城市分组即可。
 *     获取数据后，在将获取的数据和真实的地图数据做对比，验证站点个数是否完整
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class RelationGroupJobTest3 {
    @Autowired
    RestTemplate restTemplate;
    @Autowired
    RedisTemplate redisTemplate;


    //①根据充电站id，获取充电站详情，并保存进Redis
    @Test
    public void saveGroupDetail() {
        //读出的最大充电站id为47995，最小为1，说明百度是取的自增id
        //故不取前面读出来的id，自己造id
        String urlFormat = "https://oil.baidu.com/chargemap/station/info?chargeId=%s&sign=612d921d043b699d508f3f84bfeb3186&lat=9569020000&lng=91559991000";
        Map<String,ChargeGroupPo> detailMap = redisTemplate.opsForHash().entries("city_group_detail");
        if(null == detailMap){
            detailMap = Maps.newHashMap();
        }
        //避免超时，此处可以多线程，分组查询，下面的i即代表充电站id
        for (int i=1;i<=47996;i++){
            String url = String.format(urlFormat,i);
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

    /**
     * ②将充电站按城市分组，调用的高德接口，企业账户，可随意调
     * Redis中的key为城市的adCode，value为充电站id集合
     */
    @Test
    public void groupByCity() throws IOException {
        //查询出所有的充电站
        List<ChargeGroupPo> groupList= redisTemplate.opsForHash().values("city_group_detail");
        System.err.println("充电站个数为："+groupList.size());

        groupList.sort(new Comparator<ChargeGroupPo>() {
            @Override
            public int compare(ChargeGroupPo o1, ChargeGroupPo o2) {
                return Integer.valueOf(o1.getChargeId()) - Integer.valueOf(o2.getChargeId());
            }
        });
        Map<String,List<String>> city_group = redisTemplate.opsForHash().entries("city_group");
        if(null == city_group){
            city_group = Maps.newHashMap();
        }
        File erroFile = new File("C:\\Users\\LXH\\Desktop\\百度地图相关\\未找到相应城市编码的站.txt");
        //避免超时，此处可以多线程，将groupList分组查询
        for(ChargeGroupPo chargeGroup : groupList) {
            String lngLatStr = chargeGroup.getLng().concat(",").concat(chargeGroup.getLat());
            String lngStr = String.format("%.5f",Double.valueOf(chargeGroup.getLng()));
            String latStr = String.format("%.5f",Double.valueOf(chargeGroup.getLat()));
            //高德接口获取的是区县代码
            String adCodeTemp =  getAdcodeByLngLat(chargeGroup.getChargeId(),lngStr,latStr);
            //根据映射关系，转为城市代码(此处各城市的映射关系已保存进Redis，不需修改)
            //映射关系是指区县代码与城市代码的映射关系,key-区县代码，value-对应的所属城市代码
            String adCode = (String) redisTemplate.opsForHash().get("city_adcode_map",adCodeTemp);
            if(StringUtils.isEmpty(adCode)){
                this.writeByLine(chargeGroup.getChargeId()+"--"+adCodeTemp,erroFile);
                continue;
            }
            if(city_group.get(adCode) == null){
                List<String> groupIds = Lists.newArrayList();
                groupIds.add(chargeGroup.getChargeId());
                city_group.put(adCode,groupIds);
            } else {
                city_group.get(adCode).add(chargeGroup.getChargeId());
            }
        }
        //保存进Redis
        redisTemplate.opsForHash().putAll("city_group",city_group);
    }

    //根据高德经纬度获取城市adCode
    private String getAdcodeByLngLat(String chargeId,String lng,String lat) throws IOException {
        String lngLat = lng.concat(",").concat("lat");
        String url = "http://restapi.amap.com/v3/geocode/regeo?key=a00b0d10591196a068b6c360b6621cf8&location=";
        url = url.concat(lng).concat(",").concat(lat);
        File erroFile = new File("C:\\Users\\WXD\\Desktop\\百度地图相关\\根据经纬度获取城市编码出错.txt");

        JSONObject jsonObject = null;
        String areaCode = "";
        try {
            jsonObject = restTemplate.getForObject(url, JSONObject.class);
        } catch(Exception e){
            this.writeByLine("接口调用出错-"+lngLat+"-"+chargeId,erroFile);
            return areaCode;
        }
        if("1".equalsIgnoreCase(jsonObject.getString("status"))){
            areaCode = jsonObject.getJSONObject("regeocode").getJSONObject("addressComponent").getString("adcode");
            if(StringUtils.isEmpty(areaCode)|| areaCode.length()!=6){
                this.writeByLine(lngLat+"-"+chargeId,erroFile);
            }
            return areaCode;
        }else {
            this.writeByLine("接口状态出错-"+lngLat+"-"+chargeId,erroFile);
        }
        return areaCode;
    }

    //按行追加文件
    private void writeByLine(String lineStr,File  file) throws IOException {
        lineStr = lineStr + System.getProperty("line.separator");
        OutputStreamWriter write = new OutputStreamWriter(new FileOutputStream(file,true),"UTF-8");
        BufferedWriter writer = new BufferedWriter(write);
        writer.write(lineStr);
        writer.close();
    }

    //最终使用方法：获取指定城市的充电站id集合
    @Test
    public void getGroupByAdcode() {
        String adCode= "110000";
        List<String> groupList= (List<String>) redisTemplate.opsForHash().get("city_group",adCode);
        CityPo city= (CityPo) redisTemplate.opsForHash().get("city_info",adCode);
        System.err.println("所在城市"+city.getCityName()+"含有充电站个数："+groupList.size());
        System.err.println("是否包含指定充电站："+groupList.contains("40619"));
    }

    //最终使用方法：查询指定充电站id信息
    @Test
    public void getDetail() {
        String chargeId = "342";
        ChargeGroupPo chargeGroup = (ChargeGroupPo) redisTemplate.opsForHash().get("city_group_detail",chargeId);
        System.err.println(chargeGroup.getName()+"=="+chargeGroup.getLng()+"=="+chargeGroup.getLat());
    }
}
