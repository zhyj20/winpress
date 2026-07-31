package com.winpress.commercial.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("publish_task")
public class PublishTask {
  @TableId(type = IdType.AUTO)
  public Long id;
  public String taskNo;
  public Long projectId;
  public Long manuscriptId;
  public Long manuscriptVersionId;
  public Long channelId;
  public String channelType;
  public Long assignedOperatorId;
  public OffsetDateTime plannedPublishAt;
  public OffsetDateTime actualPublishAt;
  public String executionNote;
  public String exceptionReason;
  public OffsetDateTime clientAcceptedAt;
  public String status;
  public OffsetDateTime createdAt;
  public OffsetDateTime updatedAt;
}
