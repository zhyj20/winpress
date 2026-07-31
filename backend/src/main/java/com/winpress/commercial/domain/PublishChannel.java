package com.winpress.commercial.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("publish_channel")
public class PublishChannel {
  @TableId(type = IdType.AUTO)
  public Long id;
  public String channelNo;
  public String channelName;
  public String channelType;
  public String category;
  public String region;
  public String publishForm;
  public Integer expectedDays;
  public Boolean linkSupport;
  public String publicNotes;
  public String status;
  public OffsetDateTime createdAt;
  public OffsetDateTime updatedAt;
}
