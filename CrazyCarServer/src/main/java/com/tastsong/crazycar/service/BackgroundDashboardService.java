package com.tastsong.crazycar.service;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.tastsong.crazycar.mapper.EquipMapper;
import com.tastsong.crazycar.mapper.MatchMapper;
import com.tastsong.crazycar.mapper.TimeTrialMapper;
import com.tastsong.crazycar.dto.resp.RespDataStatistics;

@Service
public class BackgroundDashboardService {
    @Autowired
    private UserLoginRecordService userLoginRecordService;
    @Autowired 
    private EquipService equipService;
    @Autowired
    private TimeTrialMapper timeTrialMapper;
    @Autowired
    private MatchMapper matchMapper;

    public int getEquipNum(){
        return equipService.getEquipInfos().size();
    }

    public int getMapNum(){
        return timeTrialMapper.getTimeTrialInfos().size();
    }

    public List<RespDataStatistics> getUserLoginData(int offsetTime){
        List<RespDataStatistics> data = userLoginRecordService.getUserLoginData(offsetTime);
        return formatData(data, offsetTime);
    }

    public List<RespDataStatistics> getTimeTrialData(int offsetTime){
        List<RespDataStatistics> data = timeTrialMapper.getTimeTrialData(offsetTime);
        return formatData(data, offsetTime);
    }

    public List<RespDataStatistics> getMatchData(int offsetTime){
        List<RespDataStatistics> data = matchMapper.getMatchData(offsetTime);
        return formatData(data, offsetTime);
    }

    private List<RespDataStatistics> formatData(List<RespDataStatistics> data, int offsetTime){
        ArrayList<RespDataStatistics> result = new ArrayList<>();
        long current = System.currentTimeMillis() / 1000;
        int oneDay = 60 * 60 * 24;
        long curWeeHours = current-(current+ TimeZone.getDefault().getRawOffset()) % oneDay;
        for(int i = 0; i < offsetTime; i++){
            RespDataStatistics temp = new RespDataStatistics();
            temp.count = 0;
            temp.timestamp = curWeeHours - oneDay * (offsetTime - i - 1);
            long nextTimestaml = curWeeHours - oneDay * (offsetTime - i - 2);
            for(int k = 0; k < data.size(); k++){
                if(data.get(k).timestamp >= temp.timestamp && data.get(k).timestamp <= nextTimestaml){
                    temp.count = data.get(k).count;
                    break;
                }
            }
            result.add(temp);
        }
        return result;
    }

    public int getTimeTrialTimes(int offsetTime){
        List<RespDataStatistics> data = timeTrialMapper.getTimeTrialData(offsetTime);
        int tatal = 0;
        for(int i = 0; i < data.size(); i++){
            tatal += data.get(i).count;
        }
        // ------假数据------
        if(tatal == 0){
            tatal = 1;
        }
        // ------------------
        return tatal;
    }

    public int getMatchTimes(int offsetTime){
        List<RespDataStatistics> data = matchMapper.getMatchData(offsetTime);
        int tatal = 0;
        for(int i = 0; i < data.size(); i++){
            tatal += data.get(i).count;
        }
        // ------假数据------
        if(tatal == 0){
            tatal = 1;
        }
        // ------------------
        return tatal;
    }
}
