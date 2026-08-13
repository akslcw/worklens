package com.su.worklens_backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.su.worklens_backend.entity.DetailAccessRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DetailAccessRequestMapper extends BaseMapper<DetailAccessRequest> {

    /**
     * Atomically consumes a one-time view authorization. Returns the number of
     * updated rows: 1 for the winner, 0 when the request was already used or is
     * not in APPROVED state. The row lock taken by the UPDATE serializes
     * concurrent viewers under any isolation level.
     */
    @Update("""
            UPDATE detail_access_requests
            SET status = 'USED'
            WHERE id = #{id} AND status = 'APPROVED'
            """)
    int markUsedIfApproved(@Param("id") Long id);
}
