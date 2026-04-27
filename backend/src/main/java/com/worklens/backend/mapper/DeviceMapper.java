package com.worklens.backend.mapper;

import com.worklens.backend.entity.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceMapper {
    Device findByMacAddress(String macAddress);
    Device findById(Long id);
    int insert(Device device);
    int updateLastOnline(Long id);
    Device selectByMac(String macAddress);
    int updateHeartbeat(@Param("macAddress") String macAddress,
                        @Param("currentApp") String currentApp,
                        @Param("currentWindow") String currentWindow);
    List<Device> findAll();
}