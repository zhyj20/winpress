package com.winpress.commercial.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("manuscript")
public class Manuscript {
  @TableId(type = IdType.AUTO)
  public Long id;
  public String manuscriptNo;
  public Long projectId;
  public Long editorialTaskId;
  public String title;
  public Integer currentVersionNo;
  public Long approvedVersionId;
  public String status;
  public OffsetDateTime createdAt;
  public OffsetDateTime updatedAt;
}
