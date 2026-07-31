package com.winpress.commercial.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@TableName("project")
public class Project {
  @TableId(type = IdType.AUTO)
  public Long id;
  public String projectNo;
  public Long requirementId;
  public Long organizationId;
  public Long customerId;
  public Long activityRootProjectId;
  public String projectName;
  public Long ownerOperatorId;
  public BigDecimal budget;
  public OffsetDateTime plannedStartAt;
  public OffsetDateTime plannedEndAt;
  public String status;
  public OffsetDateTime createdAt;
  public OffsetDateTime updatedAt;
}
