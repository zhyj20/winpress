package com.winpress.commercial.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winpress.commercial.dto.WorkflowDtos.AssignSupplierChannelRequest;
import com.winpress.commercial.dto.WorkflowDtos.ChannelSelection;
import com.winpress.commercial.dto.WorkflowDtos.CreateBusinessInquiryRequest;
import com.winpress.commercial.dto.WorkflowDtos.CreateChannelRequest;
import com.winpress.commercial.dto.WorkflowDtos.CreateRequirementRequest;
import com.winpress.commercial.dto.WorkflowDtos.CreateSettlementTransactionRequest;
import com.winpress.commercial.dto.WorkflowDtos.CreateSupplierRequest;
import com.winpress.commercial.dto.WorkflowDtos.SubmitManuscriptRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateBusinessInquiryRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateChannelRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateConferenceProjectRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateSupplierOrderRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateSupplierRequest;
import com.winpress.commercial.dto.NiumediaDtos.MediaCandidate;
import com.winpress.commercial.exception.BusinessException;
import com.winpress.commercial.security.AuthPrincipal;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class WorkflowRepository {
  private static final BigDecimal ONSITE_WRITING_DAILY_RATE = new BigDecimal("980.00");
  public record RequirementCreation(Long projectId, boolean created) {}
  public enum WritingAssignmentOfferOutcome {
    OFFERED,
    NOT_OFFERABLE,
    NO_OPEN_SLOT,
    WRITER_UNAVAILABLE,
    DISTANCE_REQUIRED,
    OUT_OF_SERVICE_RADIUS,
    DUPLICATE_WRITER,
    SCHEDULE_CONFLICT
  }

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public WorkflowRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> dashboard(AuthPrincipal user) {
    // Keep every dashboard number on the same scoped query used by its destination page.
    // In particular, an operator may be able to open a project through an assigned task or
    // conference item without being the project owner.  Counting only owner_operator_id here
    // would make the dashboard under-report the list reached by the card.
    long projectCount = projectsCount(user, null, null, null, null);
    long activeProjects = projectsCount(user, null, "active", null, null);
    long taskCount = tasksCount(user, null, null, null);
    long pendingTasks = tasksCount(user, null, "pending", null);
    long mediaInvitationTasks = tasksCount(user, null, null, "MEDIA_PR");
    long pendingMediaInvitationTasks = tasksCount(user, null, "pending", "MEDIA_PR");
    long directPublishingTasks = tasksCount(user, null, null, "DIRECT_PUBLISHING");
    long pendingDirectPublishingTasks = tasksCount(user, null, "pending", "DIRECT_PUBLISHING");
    long writingAssignments = "CUSTOMER".equals(user.role())
        ? orderRecordsCount(user, "ONSITE_WRITING", null)
        : writingAssignmentCount(user, null);
    long pendingWritingAssignments = writingAssignmentCount(user, "pending");
    long conferenceProjects = projectsCount(user, null, null, null, "NEWS_CONFERENCE");
    long activeConferenceProjects = projectsCount(user, null, "active", null, "NEWS_CONFERENCE");
    long pendingConferenceWorkItems = conferenceWorkItemCount(user, true);
    long taskRecordCount = taskRecordsCount(user);
    long pendingPlatformExecutions = taskRecordsCount(user, "pendingExecution");
    long inquiryTickets = "PLATFORM_ADMIN".equals(user.role())
        ? businessInquiriesCount(null, null)
        : 0;
    long pendingInquiryTickets = "PLATFORM_ADMIN".equals(user.role())
        ? businessInquiriesCount("NEW", null)
        : 0;
    long resultCount = "CUSTOMER".equals(user.role())
        ? count("SELECT count(*) FROM result_link r JOIN project p ON p.id=r.project_id WHERE p.customer_id=?", user.userId())
        : "PUBLISH_OPERATOR".equals(user.role())
            ? count("SELECT count(*) FROM result_link r JOIN publish_task t ON t.id=r.publish_task_id WHERE t.assigned_operator_id=?", user.userId())
            : count("SELECT count(*) FROM result_link");
    long todoCount = workItemsCount(user);
    long pendingPlanConfirmations = "CUSTOMER".equals(user.role())
        ? workItemsCount(user, "planConfirmation") : 0;
    long awaitingAcceptanceTasks = tasksCount(user, null, "awaitingAcceptance", null);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("projectCount", projectCount);
    data.put("activeProjects", activeProjects);
    data.put("taskCount", taskCount);
    data.put("pendingTasks", pendingTasks);
    data.put("resultCount", resultCount);
    data.put("todoCount", todoCount);
    data.put("pendingPlanConfirmations", pendingPlanConfirmations);
    data.put("awaitingAcceptanceTasks", awaitingAcceptanceTasks);
    data.put("mediaInvitationTasks", mediaInvitationTasks);
    data.put("pendingMediaInvitationTasks", pendingMediaInvitationTasks);
    data.put("directPublishingTasks", directPublishingTasks);
    data.put("pendingDirectPublishingTasks", pendingDirectPublishingTasks);
    data.put("writingAssignments", writingAssignments);
    data.put("pendingWritingAssignments", pendingWritingAssignments);
    data.put("conferenceProjects", conferenceProjects);
    data.put("activeConferenceProjects", activeConferenceProjects);
    data.put("pendingConferenceWorkItems", pendingConferenceWorkItems);
    data.put("taskRecordCount", taskRecordCount);
    data.put("pendingPlatformExecutions", pendingPlatformExecutions);
    data.put("inquiryTickets", inquiryTickets);
    data.put("pendingInquiryTickets", pendingInquiryTickets);
    return data;
  }

  /**
   * The dashboard total and the work-items page must use the same four sources. Keep this
   * calculation beside the dashboard contract instead of treating a zero as a successful fetch.
   */
  public long workItemsCount(AuthPrincipal user) {
    return workItemsCount(user, null);
  }

  public long workItemsCount(AuthPrincipal user, String scope) {
    if ("CUSTOMER".equals(user.role())) {
      CustomerWorkItemQuery query = customerWorkItemQuery(user, scope);
      return count(
          "SELECT count(*) FROM (" + query.sql() + ") customer_work_items",
          query.args().toArray());
    }
    long pendingPublishTasks = tasksCount(user, null, "pending", null);
    long pendingWritingAssignments = writingAssignmentCount(user, "pending");
    long pendingServiceIntakes = serviceIntakeTaskCount(user, true);
    long pendingConferenceItems = conferenceWorkItemCount(user, true);
    long pendingInquiries = "PLATFORM_ADMIN".equals(user.role())
        ? businessInquiriesCount("NEW", null) : 0;
    return pendingPublishTasks + pendingWritingAssignments + pendingServiceIntakes + pendingConferenceItems + pendingInquiries;
  }

  public List<Map<String, Object>> workItems(AuthPrincipal user, int limit, int offset) {
    return workItems(user, null, limit, offset);
  }

  public List<Map<String, Object>> workItems(
      AuthPrincipal user, String scope, int limit, int offset) {
    if ("CUSTOMER".equals(user.role())) {
      CustomerWorkItemQuery query = customerWorkItemQuery(user, scope);
      List<Object> customerArgs = new ArrayList<>(query.args());
      customerArgs.add(limit);
      customerArgs.add(offset);
      return jdbc.queryForList(
          query.sql() + " ORDER BY \"updatedAt\" DESC LIMIT ? OFFSET ?",
          customerArgs.toArray());
    }
    StringBuilder sql = new StringBuilder();
    List<Object> args = new ArrayList<>();

    appendTodoSelect(sql, """
        SELECT 'PUBLISH_TASK' AS "itemType", t.id AS "itemId", t.task_no AS "recordNo", t.project_id AS "projectId",
               p.project_name AS "projectName",
               COALESCE(mpi.media_name, c.channel_name, '媒体邀请名单待项目核验') AS title, t.status,
               t.updated_at AS "updatedAt", '发布任务' AS "itemLabel"
        FROM publish_task t
        JOIN project p ON p.id=t.project_id
        LEFT JOIN publish_channel c ON c.id=t.channel_id
        LEFT JOIN media_pr_invitation mpi ON mpi.publish_task_id=t.id
        WHERE t.status NOT IN ('COMPLETED','CLIENT_ACCEPTED','NOT_PROCEEDING')
        """, user, args, "p", "t.assigned_operator_id=?");

    appendTodoSelect(sql, """
        SELECT 'WRITING_ASSIGNMENT' AS "itemType", wa.id AS "itemId", wa.assignment_no AS "recordNo", et.project_id AS "projectId",
               p.project_name AS "projectName", et.task_no AS title, wa.status,
               wa.updated_at AS "updatedAt", '云采写' AS "itemLabel"
        FROM writing_assignment wa
        JOIN editorial_task et ON et.id=wa.editorial_task_id
        JOIN project p ON p.id=et.project_id
        WHERE wa.status NOT IN ('CANCELLED','COMPLETED')
        """, user, args, "p", """
            EXISTS (
              SELECT 1
              FROM writing_assignment_member member
              JOIN writer_profile profile ON profile.id=member.writer_profile_id
              WHERE member.assignment_id=wa.id AND profile.user_id=?
            )
            """);

    appendTodoSelect(sql, """
        SELECT 'SERVICE_INTAKE' AS "itemType", sit.id AS "itemId", sit.intake_task_no AS "recordNo", sit.project_id AS "projectId",
               p.project_name AS "projectName", sit.title, sit.status,
               sit.updated_at AS "updatedAt",
               CASE WHEN sit.service_type='MEDIA_PR' THEN '媒体邀请' ELSE '直编发稿' END AS "itemLabel"
        FROM service_intake_task sit
        JOIN project p ON p.id=sit.project_id
        WHERE sit.status NOT IN ('COMPLETED','CANCELLED')
        """, user, args, "p", "sit.assigned_operator_id=?");

    appendTodoSelect(sql, """
        SELECT 'CONFERENCE_WORK_ITEM' AS "itemType", cwi.id AS "itemId", cwi.item_no AS "recordNo", cp.project_id AS "projectId",
               p.project_name AS "projectName", cwi.title AS title, cwi.status,
               cwi.updated_at AS "updatedAt", '新闻发布会' AS "itemLabel"
        FROM conference_work_item cwi
        JOIN conference_project cp ON cp.id=cwi.conference_project_id
        JOIN project p ON p.id=cp.project_id
        WHERE cwi.status<>'COMPLETED'
        """, user, args, "p", "cwi.assigned_operator_id=?");

    if ("PLATFORM_ADMIN".equals(user.role())) {
      sql.append(" UNION ALL ");
      sql.append("""
          SELECT 'BUSINESS_INQUIRY' AS "itemType", bi.id AS "itemId", bi.inquiry_no AS "recordNo", NULL::BIGINT AS "projectId",
                 NULL::VARCHAR AS "projectName", bi.company_name AS title, bi.status,
                 bi.updated_at AS "updatedAt", '商务咨询' AS "itemLabel"
          FROM business_inquiry bi
          WHERE bi.status='NEW'
          """);
    }
    sql.append(" ORDER BY 8 DESC LIMIT ? OFFSET ?");
    args.add(limit);
    args.add(offset);
    return jdbc.queryForList(sql.toString(), args.toArray());
  }

  /**
   * Customer task management is an action queue, not a mirror of the platform execution queue.
   * It therefore contains only records on which the customer can act: confirm a saved plan,
   * review a manuscript, accept a verified result, or supplement information requested by the
   * project team.
   */
  private CustomerWorkItemQuery customerWorkItemQuery(AuthPrincipal user, String scope) {
    String sql = """
        SELECT 'MANUSCRIPT_REVIEW' AS "itemType", v.id AS "itemId",
               v.version_no AS "recordNo", p.id AS "projectId", p.project_name AS "projectName",
               v.title, 'CLIENT_REVIEW' AS status, v.updated_at AS "updatedAt",
               '稿件审核' AS "itemLabel"
        FROM manuscript_version v
        JOIN manuscript m ON m.id=v.manuscript_id
        JOIN project p ON p.id=m.project_id
        WHERE p.customer_id=? AND v.status='CLIENT_REVIEW'

        UNION ALL

        SELECT 'PUBLISH_RESULT_ACCEPTANCE' AS "itemType", t.id AS "itemId",
               t.task_no AS "recordNo", p.id AS "projectId", p.project_name AS "projectName",
               CONCAT('确认成果：', COALESCE(mpi.media_name, c.channel_name, '发布结果')) AS title,
               'AWAITING_CLIENT_ACCEPTANCE' AS status, t.updated_at AS "updatedAt",
               '成果验收' AS "itemLabel"
        FROM publish_task t
        JOIN project p ON p.id=t.project_id
        LEFT JOIN publish_channel c ON c.id=t.channel_id
        LEFT JOIN media_pr_invitation mpi ON mpi.publish_task_id=t.id
        WHERE p.customer_id=? AND t.status='COMPLETED'

        UNION ALL

        SELECT 'PUBLISH_TASK_INFO' AS "itemType", t.id AS "itemId",
               t.task_no AS "recordNo", p.id AS "projectId", p.project_name AS "projectName",
               CONCAT('补充资料：', COALESCE(mpi.media_name, c.channel_name, p.project_name)) AS title,
               t.status, t.updated_at AS "updatedAt", '资料补充' AS "itemLabel"
        FROM publish_task t
        JOIN project p ON p.id=t.project_id
        LEFT JOIN publish_channel c ON c.id=t.channel_id
        LEFT JOIN media_pr_invitation mpi ON mpi.publish_task_id=t.id
        WHERE p.customer_id=? AND t.status='NEEDS_INFO'

        UNION ALL

        SELECT 'EDITORIAL_TASK_INFO' AS "itemType", et.id AS "itemId",
               et.task_no AS "recordNo", p.id AS "projectId", p.project_name AS "projectName",
               CONCAT('补充采写资料：', p.project_name) AS title, et.status,
               et.updated_at AS "updatedAt", '资料补充' AS "itemLabel"
        FROM editorial_task et
        JOIN project p ON p.id=et.project_id
        WHERE p.customer_id=? AND et.status='NEEDS_INFO'

        UNION ALL

        SELECT 'SERVICE_INTAKE_INFO' AS "itemType", sit.id AS "itemId",
               sit.intake_task_no AS "recordNo", p.id AS "projectId",
               p.project_name AS "projectName", sit.title, sit.status,
               sit.updated_at AS "updatedAt", '资料补充' AS "itemLabel"
        FROM service_intake_task sit
        JOIN project p ON p.id=sit.project_id
        WHERE p.customer_id=? AND sit.status='PENDING_INFO'

        UNION ALL

        SELECT 'CONFERENCE_WORK_ITEM_INFO' AS "itemType", cwi.id AS "itemId",
               cwi.item_no AS "recordNo", p.id AS "projectId", p.project_name AS "projectName",
               cwi.title, cwi.status, cwi.updated_at AS "updatedAt",
               '资料补充' AS "itemLabel"
        FROM conference_work_item cwi
        JOIN conference_project cp ON cp.id=cwi.conference_project_id
        JOIN project p ON p.id=cp.project_id
        WHERE p.customer_id=? AND cwi.status='NEEDS_INFO'

        UNION ALL

        SELECT 'PUBLISH_PLAN_CONFIRMATION' AS "itemType", pp.id AS "itemId",
               pp.plan_no AS "recordNo", p.id AS "projectId", p.project_name AS "projectName",
               pp.plan_name AS title, pp.status, pp.updated_at AS "updatedAt",
               '发布计划确认' AS "itemLabel"
        FROM publish_plan pp
        JOIN project p ON p.id=pp.project_id
        WHERE p.customer_id=? AND pp.status='WAITING_CONFIRMATION'
        """;
    List<Object> args = new ArrayList<>();
    for (int i = 0; i < 7; i++) args.add(user.userId());
    if ("planConfirmation".equals(scope)) {
      sql = "SELECT * FROM (" + sql + ") customer_work_item_scope "
          + "WHERE \"itemType\"='PUBLISH_PLAN_CONFIRMATION'";
    }
    return new CustomerWorkItemQuery(sql, args);
  }

  private record CustomerWorkItemQuery(String sql, List<Object> args) {}

  /**
   * Historical service records intentionally use the same role scope as the live work queue, but
   * include completed, declined and cancelled work as well. It is the source for the customer
   * facing task-record ledger; supplier orders and cost data never join this projection.
   */
  public long taskRecordsCount(AuthPrincipal user) {
    return taskRecordsCount(user, null);
  }

  public long taskRecordsCount(AuthPrincipal user, String scope) {
    TaskRecordQuery query = taskRecordQuery(user, scope);
    return count(
        "SELECT count(*) FROM (" + query.sql() + ") task_record_count",
        query.args().toArray());
  }

  public List<Map<String, Object>> taskRecords(AuthPrincipal user, int limit, int offset) {
    return taskRecords(user, null, limit, offset);
  }

  public List<Map<String, Object>> taskRecords(
      AuthPrincipal user, String scope, int limit, int offset) {
    TaskRecordQuery query = taskRecordQuery(user, scope);
    List<Object> args = new ArrayList<>(query.args());
    args.add(limit);
    args.add(offset);
    return jdbc.queryForList(
        query.sql() + " ORDER BY \"updatedAt\" DESC LIMIT ? OFFSET ?",
        args.toArray());
  }

  private TaskRecordQuery taskRecordQuery(AuthPrincipal user, String scope) {
    StringBuilder sql = new StringBuilder();
    List<Object> args = new ArrayList<>();

    appendTaskRecordSelect(sql, """
        SELECT 'PUBLISH_TASK' AS "itemType", t.id AS "itemId", t.task_no AS "recordNo",
               t.project_id AS "projectId", p.project_name AS "projectName",
               COALESCE(mpi.media_name, c.channel_name, '媒体邀请名单待项目核验') AS title,
               CASE WHEN t.channel_type='MEDIA_PR' THEN '媒体邀请' ELSE '直编发稿' END AS "itemLabel",
               t.channel_type AS "serviceType", t.status,
               assignee.display_name AS "ownerName", t.planned_publish_at AS "dueAt",
               t.actual_publish_at AS "completedAt",
               COALESCE(t.execution_note, t.exception_reason) AS note,
               t.updated_at AS "updatedAt"
        FROM publish_task t
        JOIN project p ON p.id=t.project_id
        LEFT JOIN publish_channel c ON c.id=t.channel_id
        LEFT JOIN media_pr_invitation mpi ON mpi.publish_task_id=t.id
        LEFT JOIN app_user assignee ON assignee.id=t.assigned_operator_id
        WHERE 1=1
        """, user, args, "p", "t.assigned_operator_id=?");

    appendTaskRecordSelect(sql, """
        SELECT 'WRITING_ASSIGNMENT' AS "itemType", wa.id AS "itemId", wa.assignment_no AS "recordNo",
               et.project_id AS "projectId", p.project_name AS "projectName",
               COALESCE(r.title, et.task_no) AS title, '云采写' AS "itemLabel",
               'ONSITE_WRITING' AS "serviceType",
               CASE latest_manuscript.status
                 WHEN 'CLIENT_APPROVED' THEN 'CLIENT_ACCEPTED'
                 WHEN 'CLIENT_REVIEW' THEN 'WAITING_CONFIRMATION'
                 WHEN 'CLIENT_RETURNED' THEN 'IN_PROGRESS'
                 ELSE wa.status
                END AS status,
                COALESCE(writer.display_name, assignee.display_name) AS "ownerName",
               et.due_at AS "dueAt",
               CASE
                 WHEN latest_manuscript.status='CLIENT_APPROVED' THEN latest_manuscript.updated_at
                 WHEN wa.status='COMPLETED' THEN wa.updated_at
                 ELSE NULL
               END AS "completedAt",
               COALESCE(wa.response_note, et.writing_brief) AS note,
               GREATEST(
                 wa.updated_at,
                 et.updated_at,
                 COALESCE(latest_manuscript.updated_at, wa.updated_at)
               ) AS "updatedAt"
        FROM writing_assignment wa
        JOIN editorial_task et ON et.id=wa.editorial_task_id
        JOIN project p ON p.id=et.project_id
        JOIN customer_requirement r ON r.id=et.requirement_id
        LEFT JOIN LATERAL (
          SELECT string_agg(writer_user.display_name, '、' ORDER BY writer_user.display_name) AS display_name
          FROM writing_assignment_member member
          JOIN writer_profile profile ON profile.id=member.writer_profile_id
          JOIN app_user writer_user ON writer_user.id=profile.user_id
          WHERE member.assignment_id=wa.id
            AND member.status IN ('OFFERED','ACCEPTED','COMPLETED')
        ) writer ON TRUE
        LEFT JOIN app_user assignee ON assignee.id=et.assigned_operator_id
        LEFT JOIN LATERAL (
          SELECT m.status, m.updated_at
          FROM manuscript m
          WHERE m.project_id=p.id
          ORDER BY m.updated_at DESC, m.id DESC
          LIMIT 1
        ) latest_manuscript ON TRUE
        WHERE 1=1
        """, user, args, "p", """
            EXISTS (
              SELECT 1
              FROM writing_assignment_member member
              JOIN writer_profile profile ON profile.id=member.writer_profile_id
              WHERE member.assignment_id=wa.id AND profile.user_id=?
            )
            """);

    appendTaskRecordSelect(sql, """
        SELECT 'SERVICE_INTAKE' AS "itemType", sit.id AS "itemId", sit.intake_task_no AS "recordNo",
               sit.project_id AS "projectId", p.project_name AS "projectName", sit.title,
               CASE WHEN sit.service_type='MEDIA_PR' THEN '媒体邀请' ELSE '直编发稿' END AS "itemLabel",
               sit.service_type AS "serviceType", sit.status,
               assignee.display_name AS "ownerName", NULL::TIMESTAMPTZ AS "dueAt",
               sit.completed_at AS "completedAt", sit.customer_visible_note AS note,
               sit.updated_at AS "updatedAt"
        FROM service_intake_task sit
        JOIN project p ON p.id=sit.project_id
        LEFT JOIN app_user assignee ON assignee.id=sit.assigned_operator_id
        WHERE 1=1
        """, user, args, "p", "sit.assigned_operator_id=?");

    appendTaskRecordSelect(sql, """
        SELECT 'CONFERENCE_WORK_ITEM' AS "itemType", cwi.id AS "itemId", cwi.item_no AS "recordNo",
               cp.project_id AS "projectId", p.project_name AS "projectName", cwi.title,
               '新闻发布会' AS "itemLabel", 'NEWS_CONFERENCE' AS "serviceType",
               cwi.status, assignee.display_name AS "ownerName",
               cwi.due_at AS "dueAt", cwi.completed_at AS "completedAt", cwi.note,
               cwi.updated_at AS "updatedAt"
        FROM conference_work_item cwi
        JOIN conference_project cp ON cp.id=cwi.conference_project_id
        JOIN project p ON p.id=cp.project_id
        LEFT JOIN app_user assignee ON assignee.id=cwi.assigned_operator_id
        WHERE 1=1
        """, user, args, "p", "cwi.assigned_operator_id=?");

    if ("pendingExecution".equals(scope)) {
      sql = new StringBuilder("SELECT * FROM (")
          .append(sql)
          .append("""
              ) task_record_scope
              WHERE
                ("itemType"='PUBLISH_TASK'
                  AND status IN ('PENDING_ASSIGNMENT','PENDING_EXECUTION','IN_PROGRESS','EXCEPTION'))
                OR ("itemType"='WRITING_ASSIGNMENT'
                  AND status IN ('WAITING_MATCH','OFFERED','PARTIALLY_ACCEPTED','ACCEPTED','DECLINED','IN_PROGRESS'))
                OR ("itemType"='SERVICE_INTAKE'
                  AND status IN ('PENDING_ACCEPTANCE','IN_PROGRESS'))
                OR ("itemType"='CONFERENCE_WORK_ITEM'
                  AND status IN ('PENDING','IN_PROGRESS','BLOCKED'))
              """);
    }
    return new TaskRecordQuery(sql.toString(), List.copyOf(args));
  }

  private record TaskRecordQuery(String sql, List<Object> args) {}

  /**
   * Customer order records are a distinct projection from task history. The projection covers
   * all four customer services while keeping supplier names, procurement costs and upstream
   * identifiers out of the result set.
   */
  public long orderRecordsCount(AuthPrincipal user, String serviceType, String status) {
    OrderRecordQuery query = orderRecordQuery(user, serviceType, status);
    return count("SELECT count(*) FROM (" + query.sql() + ") order_record_count", query.args().toArray());
  }

  public List<Map<String, Object>> orderRecords(
      AuthPrincipal user, String serviceType, String status, int limit, int offset) {
    OrderRecordQuery query = orderRecordQuery(user, serviceType, status);
    List<Object> args = new ArrayList<>(query.args());
    args.add(limit);
    args.add(offset);
    return jdbc.queryForList(
        query.sql() + " ORDER BY \"updatedAt\" DESC, \"recordNo\" DESC LIMIT ? OFFSET ?",
        args.toArray());
  }

  /**
   * Settlement entries are scoped through the owning project and organization. This is separate
   * from the platform settlement screen so customer queries can never inherit cross-brand rows.
   */
  public List<Map<String, Object>> customerSettlementRecords(
      AuthPrincipal user, String status, int limit, int offset) {
    return customerSettlementRecords(user, status, false, limit, offset);
  }

  public List<Map<String, Object>> customerArchivedSettlementRecords(
      AuthPrincipal user, String status, int limit, int offset) {
    return customerSettlementRecords(user, status, true, limit, offset);
  }

  private List<Map<String, Object>> customerSettlementRecords(
      AuthPrincipal user, String status, boolean archiveOnly, int limit, int offset) {
    StringBuilder sql = new StringBuilder("""
        SELECT s.settlement_no AS "settlementNo", p.id AS "projectId", p.project_no AS "projectNo",
               p.project_name AS "projectName", r.requested_service AS "serviceType",
               CASE r.requested_service
                 WHEN 'ONSITE_WRITING' THEN '云采写'
                 WHEN 'MEDIA_PR' THEN '媒体邀请'
                 WHEN 'DIRECT_PUBLISHING' THEN '直编发稿'
                 WHEN 'NEWS_CONFERENCE' THEN '举办新闻发布会'
                 ELSE '历史组合记录'
               END AS "serviceLabel",
               CASE WHEN r.requested_service IN
                 ('ONSITE_WRITING','MEDIA_PR','DIRECT_PUBLISHING','NEWS_CONFERENCE')
                 THEN FALSE ELSE TRUE END AS "archiveOnly",
               s.amount, s.paid_amount AS "paidAmount", s.currency,
               tx.adjustment_amount AS "adjustmentAmount",
               GREATEST(s.amount + tx.adjustment_amount - s.paid_amount, 0) AS "outstandingAmount",
               s.due_at AS "dueAt", s.paid_at AS "paidAt", s.invoice_no AS "invoiceNo", s.status,
               s.updated_at AS "updatedAt"
        FROM settlement_order s
        JOIN project p ON p.id=s.project_id
        JOIN customer_requirement r ON r.id=p.requirement_id
        LEFT JOIN LATERAL (
          SELECT COALESCE(SUM(
            CASE
              WHEN st.transaction_type='DEBIT_ADJUSTMENT' THEN st.amount
              WHEN st.transaction_type IN ('CREDIT_ADJUSTMENT', 'WRITE_OFF') THEN -st.amount
              ELSE 0
            END
          ), 0) AS adjustment_amount
          FROM settlement_transaction st
          WHERE st.settlement_order_id=s.id AND st.status='CONFIRMED'
        ) tx ON TRUE
        WHERE p.customer_id=? AND p.organization_id=? AND s.organization_id=p.organization_id
        """);
    List<Object> args = new ArrayList<>(List.of(user.userId(), user.organizationId()));
    sql.append(archiveOnly
        ? " AND r.requested_service NOT IN ('ONSITE_WRITING','MEDIA_PR','DIRECT_PUBLISHING','NEWS_CONFERENCE')"
        : " AND r.requested_service IN ('ONSITE_WRITING','MEDIA_PR','DIRECT_PUBLISHING','NEWS_CONFERENCE')");
    if (!blank(status)) {
      sql.append(" AND s.status=?");
      args.add(status);
    }
    sql.append(" ORDER BY s.updated_at DESC, s.settlement_no DESC LIMIT ? OFFSET ?");
    args.add(limit);
    args.add(offset);
    return jdbc.queryForList(sql.toString(), args.toArray());
  }

  public long customerSettlementRecordsCount(AuthPrincipal user, String status) {
    return customerSettlementRecordsCount(user, status, false);
  }

  public long customerArchivedSettlementRecordsCount(AuthPrincipal user, String status) {
    return customerSettlementRecordsCount(user, status, true);
  }

  private long customerSettlementRecordsCount(
      AuthPrincipal user, String status, boolean archiveOnly) {
    StringBuilder sql = new StringBuilder("""
        SELECT count(*)
        FROM settlement_order s
        JOIN project p ON p.id=s.project_id
        JOIN customer_requirement r ON r.id=p.requirement_id
        WHERE p.customer_id=? AND p.organization_id=? AND s.organization_id=p.organization_id
        """);
    List<Object> args = new ArrayList<>(List.of(user.userId(), user.organizationId()));
    sql.append(archiveOnly
        ? " AND r.requested_service NOT IN ('ONSITE_WRITING','MEDIA_PR','DIRECT_PUBLISHING','NEWS_CONFERENCE')"
        : " AND r.requested_service IN ('ONSITE_WRITING','MEDIA_PR','DIRECT_PUBLISHING','NEWS_CONFERENCE')");
    if (!blank(status)) {
      sql.append(" AND s.status=?");
      args.add(status);
    }
    return count(sql.toString(), args.toArray());
  }

  /**
   * Customer transaction history is projected through the settlement's project ownership. The
   * result deliberately excludes internal notes, actor identifiers and voiding reasons.
   */
  public List<Map<String, Object>> customerSettlementTransactions(
      AuthPrincipal user, String transactionType, String status, int limit, int offset) {
    return customerSettlementTransactions(
        user, transactionType, status, false, limit, offset);
  }

  public List<Map<String, Object>> customerArchivedSettlementTransactions(
      AuthPrincipal user, String transactionType, String status, int limit, int offset) {
    return customerSettlementTransactions(
        user, transactionType, status, true, limit, offset);
  }

  private List<Map<String, Object>> customerSettlementTransactions(
      AuthPrincipal user, String transactionType, String status, boolean archiveOnly,
      int limit, int offset) {
    StringBuilder sql = new StringBuilder("""
        SELECT st.transaction_no AS "transactionNo", s.settlement_no AS "settlementNo",
               p.id AS "projectId", p.project_no AS "projectNo", p.project_name AS "projectName",
               r.requested_service AS "serviceType",
               CASE r.requested_service
                 WHEN 'ONSITE_WRITING' THEN '云采写'
                 WHEN 'MEDIA_PR' THEN '媒体邀请'
                 WHEN 'DIRECT_PUBLISHING' THEN '直编发稿'
                 WHEN 'NEWS_CONFERENCE' THEN '举办新闻发布会'
                 ELSE '历史组合记录'
               END AS "serviceLabel",
               CASE WHEN r.requested_service IN
                 ('ONSITE_WRITING','MEDIA_PR','DIRECT_PUBLISHING','NEWS_CONFERENCE')
                 THEN FALSE ELSE TRUE END AS "archiveOnly",
               st.transaction_type AS "transactionType",
               CASE st.transaction_type
                 WHEN 'PAYMENT' THEN '收款'
                 WHEN 'REFUND' THEN '退款'
                 WHEN 'CREDIT_ADJUSTMENT' THEN '贷项调整'
                 WHEN 'DEBIT_ADJUSTMENT' THEN '借项调整'
                 WHEN 'WRITE_OFF' THEN '核销'
               END AS "transactionLabel",
               st.amount, st.currency, st.occurred_at AS "occurredAt",
               st.reference_no AS "referenceNo", st.customer_note AS "customerNote",
               st.status, st.created_at AS "createdAt", st.updated_at AS "updatedAt"
        FROM settlement_transaction st
        JOIN settlement_order s ON s.id=st.settlement_order_id
        JOIN project p ON p.id=s.project_id
        JOIN customer_requirement r ON r.id=p.requirement_id
        WHERE p.customer_id=? AND p.organization_id=? AND s.organization_id=p.organization_id
        """);
    List<Object> args = new ArrayList<>(List.of(user.userId(), user.organizationId()));
    sql.append(archiveOnly
        ? " AND r.requested_service NOT IN ('ONSITE_WRITING','MEDIA_PR','DIRECT_PUBLISHING','NEWS_CONFERENCE')"
        : " AND r.requested_service IN ('ONSITE_WRITING','MEDIA_PR','DIRECT_PUBLISHING','NEWS_CONFERENCE')");
    if (!blank(transactionType)) {
      sql.append(" AND st.transaction_type=?");
      args.add(transactionType);
    }
    if (!blank(status)) {
      sql.append(" AND st.status=?");
      args.add(status);
    }
    sql.append(" ORDER BY st.occurred_at DESC, st.id DESC LIMIT ? OFFSET ?");
    args.add(limit);
    args.add(offset);
    return jdbc.queryForList(sql.toString(), args.toArray());
  }

  public long customerSettlementTransactionsCount(
      AuthPrincipal user, String transactionType, String status) {
    return customerSettlementTransactionsCount(user, transactionType, status, false);
  }

  public long customerArchivedSettlementTransactionsCount(
      AuthPrincipal user, String transactionType, String status) {
    return customerSettlementTransactionsCount(user, transactionType, status, true);
  }

  private long customerSettlementTransactionsCount(
      AuthPrincipal user, String transactionType, String status, boolean archiveOnly) {
    StringBuilder sql = new StringBuilder("""
        SELECT count(*)
        FROM settlement_transaction st
        JOIN settlement_order s ON s.id=st.settlement_order_id
        JOIN project p ON p.id=s.project_id
        JOIN customer_requirement r ON r.id=p.requirement_id
        WHERE p.customer_id=? AND p.organization_id=? AND s.organization_id=p.organization_id
        """);
    List<Object> args = new ArrayList<>(List.of(user.userId(), user.organizationId()));
    sql.append(archiveOnly
        ? " AND r.requested_service NOT IN ('ONSITE_WRITING','MEDIA_PR','DIRECT_PUBLISHING','NEWS_CONFERENCE')"
        : " AND r.requested_service IN ('ONSITE_WRITING','MEDIA_PR','DIRECT_PUBLISHING','NEWS_CONFERENCE')");
    if (!blank(transactionType)) {
      sql.append(" AND st.transaction_type=?");
      args.add(transactionType);
    }
    if (!blank(status)) {
      sql.append(" AND st.status=?");
      args.add(status);
    }
    return count(sql.toString(), args.toArray());
  }

  private OrderRecordQuery orderRecordQuery(
      AuthPrincipal user, String serviceType, String status) {
    // A customer service order is created by the requirement submission and keeps that stable
    // public identity throughout execution. Assignments, publish tasks and supplier orders are
    // execution records; they may be added, retried or split without multiplying or removing
    // the customer's original order row.
    StringBuilder sql = new StringBuilder("""
        SELECT 'SERVICE_ORDER' AS "itemType", r.id AS "itemId",
               r.requirement_no AS "recordNo",
               p.id AS "projectId", p.project_no AS "projectNo", p.project_name AS "projectName",
               r.requested_service AS "serviceType",
               CASE r.requested_service
                 WHEN 'ONSITE_WRITING' THEN '云采写'
                 WHEN 'MEDIA_PR' THEN '媒体邀请'
                 WHEN 'DIRECT_PUBLISHING' THEN '直编发稿'
                 WHEN 'NEWS_CONFERENCE' THEN '举办新闻发布会'
               END AS "serviceLabel",
               CASE
                 WHEN r.requested_service IN ('MEDIA_PR','DIRECT_PUBLISHING')
                   THEN COALESCE(NULLIF(latest_plan.estimated_amount, 0), r.estimated_amount)
                 ELSE r.estimated_amount
               END AS amount,
               COALESCE(latest_plan.currency, 'CNY')::VARCHAR AS currency,
               CASE
                 WHEN r.requested_service='ONSITE_WRITING'
                   THEN CASE latest_manuscript.status
                     WHEN 'CLIENT_APPROVED' THEN 'CLIENT_ACCEPTED'
                     WHEN 'CLIENT_REVIEW' THEN 'WAITING_CONFIRMATION'
                     WHEN 'CLIENT_RETURNED' THEN 'IN_PROGRESS'
                     ELSE COALESCE(latest_writing.status, r.status)
                   END
                 WHEN r.requested_service='NEWS_CONFERENCE'
                   THEN COALESCE(cp.status, r.status)
                 WHEN latest_plan.status='CANCELLED' THEN 'CANCELLED'
                 WHEN latest_plan.status='WAITING_CONFIRMATION' THEN 'WAITING_CONFIRMATION'
                 WHEN execution.total_count > 0 AND execution.exception_count > 0 THEN 'EXCEPTION'
                 WHEN execution.total_count > 0
                      AND execution.client_accepted_count=execution.total_count THEN 'CLIENT_ACCEPTED'
                 WHEN execution.total_count > 0
                      AND execution.not_proceeding_count=execution.total_count THEN 'NOT_PROCEEDING'
                 WHEN execution.total_count > 0
                      AND execution.terminal_count=execution.total_count THEN 'COMPLETED'
                 WHEN execution.in_progress_count > 0 THEN 'IN_PROGRESS'
                 WHEN execution.pending_execution_count > 0 THEN 'PENDING_EXECUTION'
                 WHEN execution.pending_assignment_count > 0 THEN 'PENDING_ASSIGNMENT'
                 WHEN latest_plan.status IS NOT NULL THEN latest_plan.status
                 WHEN sit.status IS NOT NULL THEN sit.status
                 ELSE r.status
               END AS status,
               CASE r.requested_service
                 WHEN 'ONSITE_WRITING' THEN
                   COALESCE(latest_writing.service_location, r.event_location, '服务地点待补充')
                 WHEN 'NEWS_CONFERENCE' THEN
                   COALESCE(cp.event_location, r.event_location, '会务信息待补充')
                 WHEN 'MEDIA_PR' THEN
                   CASE
                     WHEN latest_plan.id IS NULL THEN '待筛选媒体或确认邀请范围'
                     WHEN latest_plan.item_count=1 THEN
                       COALESCE(latest_plan.single_item_name, latest_plan.plan_name)
                     ELSE latest_plan.plan_name || ' · ' || latest_plan.item_count || ' 项'
                   END
                 WHEN 'DIRECT_PUBLISHING' THEN
                   CASE
                     WHEN latest_plan.id IS NULL THEN '待选择渠道或确认发稿范围'
                     WHEN latest_plan.item_count=1 THEN
                       COALESCE(latest_plan.single_item_name, latest_plan.plan_name)
                     ELSE latest_plan.plan_name || ' · ' || latest_plan.item_count || ' 项'
                   END
               END AS "itemDetail",
               owner.display_name AS "ownerName", r.created_at AS "createdAt",
               GREATEST(
                 r.updated_at,
                 p.updated_at,
                 COALESCE(latest_writing.updated_at, r.updated_at),
                 COALESCE(latest_manuscript.updated_at, r.updated_at),
                 COALESCE(cp.updated_at, r.updated_at),
                 COALESCE(sit.updated_at, r.updated_at),
                 COALESCE(latest_plan.updated_at, r.updated_at),
                 COALESCE(execution.updated_at, r.updated_at)
               ) AS "updatedAt"
        FROM customer_requirement r
        JOIN project p ON p.requirement_id=r.id
        LEFT JOIN app_user owner ON owner.id=p.owner_operator_id
        LEFT JOIN conference_project cp ON cp.project_id=p.id
        LEFT JOIN service_intake_task sit ON sit.project_id=p.id
        LEFT JOIN LATERAL (
          SELECT wa.status, wa.service_location, wa.updated_at
          FROM writing_assignment wa
          JOIN editorial_task et ON et.id=wa.editorial_task_id
          WHERE et.project_id=p.id
          ORDER BY wa.updated_at DESC, wa.id DESC
          LIMIT 1
        ) latest_writing ON TRUE
        LEFT JOIN LATERAL (
          SELECT m.status, m.updated_at
          FROM manuscript m
          WHERE m.project_id=p.id
          ORDER BY m.updated_at DESC, m.id DESC
          LIMIT 1
        ) latest_manuscript ON TRUE
        LEFT JOIN LATERAL (
          SELECT pp.id, pp.plan_name, pp.estimated_amount, pp.currency, pp.status, pp.updated_at,
                 count(ppi.id)::INT AS item_count,
                 CASE WHEN count(ppi.id)=1
                   THEN max(COALESCE(NULLIF(ppi.media_name, ''), c.channel_name))
                   ELSE NULL
                 END AS single_item_name
          FROM publish_plan pp
          LEFT JOIN publish_plan_item ppi ON ppi.publish_plan_id=pp.id
          LEFT JOIN publish_channel c ON c.id=ppi.channel_id
          WHERE pp.project_id=p.id
          GROUP BY pp.id, pp.plan_name, pp.estimated_amount, pp.currency, pp.status, pp.updated_at
          ORDER BY pp.updated_at DESC, pp.id DESC
          LIMIT 1
        ) latest_plan ON TRUE
        LEFT JOIN LATERAL (
          SELECT count(*)::INT AS total_count,
                 count(*) FILTER (WHERE t.status='EXCEPTION')::INT AS exception_count,
                 count(*) FILTER (WHERE t.status='CLIENT_ACCEPTED')::INT AS client_accepted_count,
                 count(*) FILTER (WHERE t.status IN ('COMPLETED','CLIENT_ACCEPTED'))::INT AS finished_count,
                 count(*) FILTER (WHERE t.status='NOT_PROCEEDING')::INT AS not_proceeding_count,
                 count(*) FILTER (
                   WHERE t.status IN ('COMPLETED','CLIENT_ACCEPTED','NOT_PROCEEDING')
                 )::INT AS terminal_count,
                 count(*) FILTER (WHERE t.status='IN_PROGRESS')::INT AS in_progress_count,
                 count(*) FILTER (WHERE t.status='PENDING_EXECUTION')::INT AS pending_execution_count,
                 count(*) FILTER (WHERE t.status='PENDING_ASSIGNMENT')::INT AS pending_assignment_count,
                 max(t.updated_at) AS updated_at
          FROM publish_task t
          JOIN publish_plan_item ppi ON ppi.id=t.publish_plan_item_id
          WHERE ppi.publish_plan_id=latest_plan.id
        ) execution ON TRUE
        WHERE r.requested_service IN
          ('ONSITE_WRITING','MEDIA_PR','DIRECT_PUBLISHING','NEWS_CONFERENCE')
        """);
    List<Object> args = projectScope(sql, user);
    sql = new StringBuilder("SELECT * FROM (").append(sql).append(") order_records WHERE 1=1");
    if (!blank(serviceType)) {
      sql.append(" AND \"serviceType\"=?");
      args.add(serviceType);
    }
    if (!blank(status)) {
      sql.append(" AND status=?");
      args.add(status);
    }
    return new OrderRecordQuery(sql.toString(), args);
  }

  private record OrderRecordQuery(String sql, List<Object> args) {}

  private void appendTodoSelect(
      StringBuilder sql, String select, AuthPrincipal user, List<Object> args,
      String projectAlias, String operatorPredicate) {
    if (sql.length() > 0) sql.append(" UNION ALL ");
    sql.append(select);
    if ("CUSTOMER".equals(user.role())) {
      sql.append(" AND ").append(projectAlias).append(".customer_id=?");
      args.add(user.userId());
    } else if ("PUBLISH_OPERATOR".equals(user.role())) {
      sql.append(" AND ").append(operatorPredicate);
      args.add(user.userId());
    }
  }

  private void appendTaskRecordSelect(
      StringBuilder sql, String select, AuthPrincipal user, List<Object> args,
      String projectAlias, String operatorPredicate) {
    if (sql.length() > 0) sql.append(" UNION ALL ");
    sql.append(select);
    if ("CUSTOMER".equals(user.role())) {
      sql.append(" AND ").append(projectAlias).append(".customer_id=?");
      args.add(user.userId());
    } else if ("PUBLISH_OPERATOR".equals(user.role())) {
      sql.append(" AND ").append(operatorPredicate);
      args.add(user.userId());
    }
  }

  private long writingAssignmentCount(AuthPrincipal user, String scope) {
    StringBuilder sql = new StringBuilder("""
        SELECT count(*) FROM writing_assignment wa
        JOIN editorial_task et ON et.id=wa.editorial_task_id
        JOIN project p ON p.id=et.project_id
        WHERE 1=1
        """);
    List<Object> args = new ArrayList<>();
    if ("CUSTOMER".equals(user.role())) {
      sql.append(" AND p.customer_id=?");
      args.add(user.userId());
    } else if ("PUBLISH_OPERATOR".equals(user.role())) {
      sql.append("""
           AND EXISTS (
             SELECT 1
             FROM writing_assignment_member member
             JOIN writer_profile profile ON profile.id=member.writer_profile_id
             WHERE member.assignment_id=wa.id AND profile.user_id=?
           )
          """);
      args.add(user.userId());
    }
    if ("pending".equals(scope)) {
      sql.append(" AND wa.status NOT IN ('CANCELLED','COMPLETED')");
    }
    return count(sql.toString(), args.toArray());
  }

  private long serviceIntakeTaskCount(AuthPrincipal user, boolean pendingOnly) {
    StringBuilder sql = new StringBuilder("""
        SELECT count(*) FROM service_intake_task sit
        JOIN project p ON p.id=sit.project_id
        WHERE 1=1
        """);
    List<Object> args = new ArrayList<>();
    if ("CUSTOMER".equals(user.role())) {
      sql.append(" AND p.customer_id=?");
      args.add(user.userId());
    } else if ("PUBLISH_OPERATOR".equals(user.role())) {
      sql.append(" AND sit.assigned_operator_id=?");
      args.add(user.userId());
    }
    if (pendingOnly) sql.append(" AND sit.status NOT IN ('COMPLETED','CANCELLED')");
    return count(sql.toString(), args.toArray());
  }

  private long conferenceWorkItemCount(AuthPrincipal user, boolean pendingOnly) {
    StringBuilder sql = new StringBuilder("""
        SELECT count(*) FROM conference_work_item cwi
        JOIN conference_project cp ON cp.id=cwi.conference_project_id
        JOIN project p ON p.id=cp.project_id
        WHERE 1=1
        """);
    List<Object> args = new ArrayList<>();
    if ("CUSTOMER".equals(user.role())) {
      sql.append(" AND p.customer_id=?");
      args.add(user.userId());
    } else if ("PUBLISH_OPERATOR".equals(user.role())) {
      sql.append(" AND cwi.assigned_operator_id=?");
      args.add(user.userId());
    }
    if (pendingOnly) {
      sql.append(" AND cwi.status NOT IN ('COMPLETED')");
    }
    return count(sql.toString(), args.toArray());
  }

  /**
   * Resolves the stable root of an activity only when the target belongs to the same customer and
   * organization and is one of the current four independent services. This is intentionally
   * narrower than general project visibility: historical combined-service records remain
   * viewable archives but cannot become roots for new orders.
   */
  public Long activityRootProjectId(AuthPrincipal user, Long relatedProjectId) {
    if (relatedProjectId == null || relatedProjectId < 1) return null;
    List<Long> roots = jdbc.query(
        """
        SELECT COALESCE(p.activity_root_project_id, p.id)
        FROM project p
        JOIN customer_requirement r ON r.id=p.requirement_id
        LEFT JOIN project root ON root.id=p.activity_root_project_id
        LEFT JOIN customer_requirement root_requirement ON root_requirement.id=root.requirement_id
        WHERE p.id=? AND p.customer_id=? AND p.organization_id=?
          AND (
            (
              p.activity_root_project_id IS NULL
              AND r.requested_service IN ('ONSITE_WRITING', 'MEDIA_PR', 'DIRECT_PUBLISHING', 'NEWS_CONFERENCE')
            )
            OR (
              root.customer_id=p.customer_id
              AND root.organization_id=p.organization_id
              AND root.activity_root_project_id IS NULL
              AND root_requirement.requested_service IN ('ONSITE_WRITING', 'MEDIA_PR', 'DIRECT_PUBLISHING', 'NEWS_CONFERENCE')
            )
          )
        """,
        (rs, rowNum) -> rs.getLong(1), relatedProjectId, user.userId(), user.organizationId());
    return roots.isEmpty() ? null : roots.get(0);
  }

  @Transactional
  public RequirementCreation existingRequirement(
      AuthPrincipal user, String submissionKey, String submissionHash) {
    String lockKey = user.userId() + ":" + user.organizationId() + ":" + submissionKey;
    jdbc.queryForObject(
        "SELECT pg_advisory_xact_lock(hashtextextended(?, 0)), 1",
        (rs, rowNum) -> rs.getInt(2),
        lockKey);
    List<Map<String, Object>> existing = jdbc.queryForList("""
        SELECT p.id AS "projectId", r.submission_hash AS "submissionHash"
        FROM customer_requirement r
        JOIN project p ON p.requirement_id=r.id
        WHERE r.customer_id=? AND r.organization_id=? AND r.submission_key=?
        """, user.userId(), user.organizationId(), submissionKey);
    if (!existing.isEmpty()) {
      Map<String, Object> row = existing.get(0);
      if (!submissionHash.equals(String.valueOf(row.get("submissionHash")))) {
        throw new BusinessException(
            "IDEMPOTENCY_KEY_REUSED",
            "本次请求标识已用于另一份服务需求，请刷新页面后重新提交",
            HttpStatus.CONFLICT);
      }
      return new RequirementCreation(((Number) row.get("projectId")).longValue(), false);
    }
    return null;
  }

  @Transactional
  public RequirementCreation createRequirement(
      AuthPrincipal user,
      CreateRequirementRequest request,
      String submissionKey,
      String submissionHash) {
    RequirementCreation existing = existingRequirement(user, submissionKey, submissionHash);
    if (existing != null) return existing;
    boolean onsite = "ONSITE_WRITING".equals(request.requestedService());
    boolean conference = "NEWS_CONFERENCE".equals(request.requestedService());
    Long activityRootProjectId = request.relatedProjectId() == null
        ? null : activityRootProjectId(user, request.relatedProjectId());
    if (request.relatedProjectId() != null && activityRootProjectId == null) {
      throw new BusinessException("RELATED_PROJECT_INVALID",
          "关联项目不存在，或不属于当前客户组织", HttpStatus.BAD_REQUEST);
    }
    int serviceDays = onsite ? request.serviceDays() : 1;
    int writerCount = onsite ? request.writerCount() : 1;
    BigDecimal unitPrice = onsite ? ONSITE_WRITING_DAILY_RATE : null;
    BigDecimal estimatedAmount = onsite ? unitPrice.multiply(BigDecimal.valueOf(serviceDays)).multiply(BigDecimal.valueOf(writerCount)) : null;
    KeyHolder requirementKey = new GeneratedKeyHolder();
    String requirementNo = no("REQ");
    jdbc.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO customer_requirement
          (requirement_no, customer_id, organization_id, title, event_time, event_location, facts,
           objective, target_audience, requested_service, service_days, writer_count, unit_price,
           estimated_amount, onsite_contact_name, onsite_contact_mobile, deliverable_requirement,
           matching_preference, due_at, submission_key, submission_hash, status)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SUBMITTED')
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, requirementNo);
      ps.setLong(2, user.userId());
      ps.setLong(3, user.organizationId());
      ps.setString(4, request.title());
      ps.setObject(5, request.eventTime());
      ps.setString(6, request.eventLocation());
      ps.setString(7, request.facts());
      ps.setString(8, request.objective());
      ps.setString(9, request.targetAudience());
      ps.setString(10, request.requestedService());
      ps.setInt(11, serviceDays);
      ps.setInt(12, writerCount);
      ps.setBigDecimal(13, unitPrice);
      ps.setBigDecimal(14, estimatedAmount);
      ps.setString(15, onsite ? request.onsiteContactName() : null);
      ps.setString(16, onsite ? request.onsiteContactMobile() : null);
      ps.setString(17, request.deliverableRequirement());
      ps.setString(18, onsite ? "NEAREST_AVAILABLE" : "EXPERIENCE_FIRST");
      ps.setObject(19, request.dueAt());
      ps.setString(20, submissionKey);
      ps.setString(21, submissionHash);
      return ps;
    }, requirementKey);
    Long requirementId = key(requirementKey);

    KeyHolder projectKey = new GeneratedKeyHolder();
    String projectNo = no("PRJ");
    jdbc.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO project
          (project_no, requirement_id, organization_id, customer_id, activity_root_project_id,
           project_name, budget, planned_start_at, planned_end_at, status)
          VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP), ?, 'PLANNING')
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, projectNo);
      ps.setLong(2, requirementId);
      ps.setLong(3, user.organizationId());
      ps.setLong(4, user.userId());
      ps.setObject(5, activityRootProjectId);
      ps.setString(6, request.title());
      ps.setBigDecimal(7, estimatedAmount);
      ps.setObject(8, request.eventTime());
      ps.setObject(9, request.dueAt());
      return ps;
    }, projectKey);
    Long projectId = key(projectKey);

    if (conference) {
      KeyHolder conferenceKey = new GeneratedKeyHolder();
      String conferenceNo = no("CNF");
      jdbc.update(connection -> {
        PreparedStatement ps = connection.prepareStatement("""
            INSERT INTO conference_project
            (conference_no, project_id, conference_type, conference_format, theme, event_time,
             event_location, attendee_scale, media_goal, communication_goal,
             agenda_status, venue_status, contact_name, contact_mobile, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_SCOPE')
            """, Statement.RETURN_GENERATED_KEYS);
        ps.setString(1, conferenceNo);
        ps.setLong(2, projectId);
        ps.setString(3, blankToNull(request.conferenceType()));
        ps.setString(4, blankToNull(request.conferenceFormat()));
        ps.setString(5, request.title());
        ps.setObject(6, request.eventTime());
        ps.setString(7, blankToNull(request.eventLocation()));
        ps.setString(8, request.conferenceScale());
        ps.setString(9, blankToNull(request.conferenceMediaGoal()));
        ps.setString(10, blankToNull(request.objective()));
        ps.setString(11, defaultIfBlank(request.conferenceAgendaStatus(), "PREPARING"));
        ps.setString(12, defaultIfBlank(request.conferenceVenueStatus(), "PENDING"));
        ps.setString(13, request.conferenceContactName());
        ps.setString(14, request.conferenceContactMobile());
        return ps;
      }, conferenceKey);
      Long conferenceId = key(conferenceKey);
      Object[][] workItems = {
          {1, "确认发布目标与项目范围", "核对发布主题、举办形式、时间地点、受众和成功标准。"},
          {2, "确定议程、嘉宾与发言分工", "确认议程节奏、主讲嘉宾、主持人、致辞与问答安排。"},
          {3, "落实场地、舞台与现场动线", "确认场地、视觉物料、设备、签到区、采访区和应急方案。"},
          {4, "准备新闻材料与问答口径", "整理新闻稿、背景资料、嘉宾信息、图片、数据出处和媒体问答。"},
          {5, "建立拟邀媒体清单", "按行业线口、城市、媒体属性和采访需求形成分层拟邀名单。"},
          {6, "执行媒体邀请与到场确认", "记录邀请、反馈、到场、采访安排及临时变更；媒体保持独立判断。"},
          {7, "统筹现场接待、采访与采写", "推进媒体签到、嘉宾采访、现场素材采集和云采写交付。"},
          {8, "安排会后发稿与渠道发布", "确认定稿版本、直编发稿计划和发布时间。"},
          {9, "核验成果并完成项目复盘", "归档报道与发布链接，核验异常、结算事项和后续传播建议。"}
      };
      for (Object[] item : workItems) {
        int sortOrder = (Integer) item[0];
        jdbc.update("""
            INSERT INTO conference_work_item
            (item_no, conference_project_id, sort_order, phase, title, detail, due_at, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING')
            """, no("CNF-ITM"), conferenceId, sortOrder, conferenceWorkItemPhase(sortOrder),
            item[1], item[2], conferenceWorkItemDueAt(request.eventTime(), sortOrder));
      }
      Map<String, Object> conferenceAudit = new LinkedHashMap<>();
      conferenceAudit.put("projectId", projectId);
      if (!blank(request.conferenceType())) {
        conferenceAudit.put("conferenceType", request.conferenceType());
      }
      log(user, "CREATE_NEWS_CONFERENCE", "CONFERENCE_PROJECT", conferenceNo, conferenceAudit);
    }

    if ("ONSITE_WRITING".equals(request.requestedService())) {
      KeyHolder editorialKey = new GeneratedKeyHolder();
      jdbc.update(connection -> {
        PreparedStatement ps = connection.prepareStatement("""
            INSERT INTO editorial_task (task_no, project_id, requirement_id, due_at, status)
            VALUES (?, ?, ?, ?, 'PENDING_ASSIGNMENT')
            """, Statement.RETURN_GENERATED_KEYS);
        ps.setString(1, no("EDT"));
        ps.setLong(2, projectId);
        ps.setLong(3, requirementId);
        ps.setObject(4, request.dueAt());
        return ps;
      }, editorialKey);
      Long editorialTaskId = key(editorialKey);
      if (onsite) {
        jdbc.update("""
            INSERT INTO writing_assignment
            (assignment_no, editorial_task_id, matching_mode, service_location, service_days, writer_count,
             unit_price_snapshot, estimated_amount_snapshot, status)
            VALUES (?, ?, 'NEAREST_AVAILABLE', ?, ?, ?, ?, ?, 'WAITING_MATCH')
            """, no("WRT-ASG"), editorialTaskId, request.eventLocation(), serviceDays, writerCount,
            unitPrice, estimatedAmount);
      }
    }
    if ("MEDIA_PR".equals(request.requestedService()) || "DIRECT_PUBLISHING".equals(request.requestedService())) {
      String title = "MEDIA_PR".equals(request.requestedService())
          ? "确认媒体邀请范围" : "确认稿件与发稿范围";
      jdbc.update("""
          INSERT INTO service_intake_task
          (intake_task_no, project_id, service_type, title, customer_visible_note, status)
          VALUES (?, ?, ?, ?, ?, 'PENDING_ACCEPTANCE')
          """, no("INTAKE"), projectId, request.requestedService(), title,
          "需求已提交，平台正在确认服务范围。");
    }
    log(user, "CREATE_REQUIREMENT", "REQUIREMENT", requirementNo, Map.of("projectNo", projectNo));
    return new RequirementCreation(projectId, true);
  }

  public List<Map<String, Object>> requirements(AuthPrincipal user, String status, int limit, int offset) {
    StringBuilder sql = new StringBuilder("""
        SELECT r.id, r.requirement_no AS "requirementNo", r.title, r.event_time AS "eventTime",
               r.event_location AS "eventLocation", r.requested_service AS "requestedService",
               r.service_days AS "serviceDays", r.writer_count AS "writerCount",
               r.unit_price AS "unitPrice", r.estimated_amount AS "estimatedAmount",
               r.due_at AS "dueAt", r.status, r.created_at AS "createdAt",
               u.display_name AS "customerName", o.name AS "organizationName"
        FROM customer_requirement r
        JOIN app_user u ON u.id=r.customer_id JOIN organization o ON o.id=r.organization_id
        WHERE 1=1
        """);
    List<Object> args = new ArrayList<>();
    if ("CUSTOMER".equals(user.role())) { sql.append(" AND r.customer_id=?"); args.add(user.userId()); }
    if (status != null && !status.isBlank()) { sql.append(" AND r.status=?"); args.add(status); }
    sql.append(" ORDER BY r.created_at DESC LIMIT ? OFFSET ?"); args.add(limit); args.add(offset);
    return jdbc.queryForList(sql.toString(), args.toArray());
  }

  public long requirementsCount(AuthPrincipal user, String status) {
    StringBuilder sql = new StringBuilder("SELECT count(*) FROM customer_requirement r WHERE 1=1");
    List<Object> args = new ArrayList<>();
    if ("CUSTOMER".equals(user.role())) { sql.append(" AND r.customer_id=?"); args.add(user.userId()); }
    if (status != null && !status.isBlank()) { sql.append(" AND r.status=?"); args.add(status); }
    return count(sql.toString(), args.toArray());
  }

  public List<Map<String, Object>> projects(
      AuthPrincipal user, String status, String scope, String keyword, String serviceType, int limit, int offset) {
    StringBuilder sql = new StringBuilder("""
        SELECT p.id, p.project_no AS "projectNo", p.project_name AS "projectName", p.status,
               p.budget, p.planned_end_at AS "plannedEndAt", p.created_at AS "createdAt",
               cu.display_name AS "customerName", o.name AS "organizationName",
               op.display_name AS "operatorName",
               (SELECT count(*) FROM publish_task t WHERE t.project_id=p.id) AS "taskCount",
               (SELECT count(*) FROM result_link r WHERE r.project_id=p.id AND r.status='VERIFIED') AS "resultCount",
               (SELECT m.status FROM manuscript m WHERE m.project_id=p.id ORDER BY m.updated_at DESC LIMIT 1) AS "manuscriptStatus",
               EXISTS (
                 SELECT 1 FROM manuscript m
                 WHERE m.project_id=p.id AND m.approved_version_id IS NOT NULL
               ) AS "hasApprovedManuscript"
        FROM project p
        JOIN app_user cu ON cu.id=p.customer_id
        JOIN organization o ON o.id=p.organization_id
        LEFT JOIN app_user op ON op.id=p.owner_operator_id
        WHERE 1=1
        """);
    List<Object> args = projectScope(sql, user);
    if (status != null && !status.isBlank()) { sql.append(" AND p.status=?"); args.add(status); }
    if ("active".equals(scope)) { sql.append(" AND p.status NOT IN ('COMPLETED','ARCHIVED')"); }
    if (keyword != null && !keyword.isBlank()) { sql.append(" AND (p.project_name ILIKE ? OR p.project_no ILIKE ?)"); args.add("%"+keyword+"%"); args.add("%"+keyword+"%"); }
    if (serviceType != null && !serviceType.isBlank()) {
      sql.append(" AND EXISTS (SELECT 1 FROM customer_requirement cr WHERE cr.id=p.requirement_id AND cr.requested_service=?)");
      args.add(serviceType);
    }
    sql.append(" ORDER BY p.updated_at DESC LIMIT ? OFFSET ?"); args.add(limit); args.add(offset);
    return jdbc.queryForList(sql.toString(), args.toArray());
  }

  public long projectsCount(AuthPrincipal user, String status, String scope, String keyword, String serviceType) {
    StringBuilder sql = new StringBuilder("SELECT count(*) FROM project p WHERE 1=1");
    List<Object> args = projectScope(sql, user);
    if (status != null && !status.isBlank()) { sql.append(" AND p.status=?"); args.add(status); }
    if ("active".equals(scope)) { sql.append(" AND p.status NOT IN ('COMPLETED','ARCHIVED')"); }
    if (keyword != null && !keyword.isBlank()) { sql.append(" AND (p.project_name ILIKE ? OR p.project_no ILIKE ?)"); args.add("%"+keyword+"%"); args.add("%"+keyword+"%"); }
    if (serviceType != null && !serviceType.isBlank()) {
      sql.append(" AND EXISTS (SELECT 1 FROM customer_requirement cr WHERE cr.id=p.requirement_id AND cr.requested_service=?)");
      args.add(serviceType);
    }
    return count(sql.toString(), args.toArray());
  }

  public boolean canViewProject(AuthPrincipal user, Long projectId) {
    StringBuilder sql = new StringBuilder("SELECT count(*) FROM project p WHERE p.id=?");
    List<Object> args = new ArrayList<>(List.of(projectId));
    args.addAll(projectScope(sql, user));
    return count(sql.toString(), args.toArray()) > 0;
  }

  public Map<String, Object> projectDetail(Long projectId) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("project", one("""
        SELECT p.id, p.project_no AS "projectNo", p.project_name AS "projectName", p.status, p.budget,
               COALESCE(p.activity_root_project_id, p.id) AS "activityRootProjectId",
               p.planned_start_at AS "plannedStartAt", p.planned_end_at AS "plannedEndAt",
               cu.display_name AS "customerName", o.name AS "organizationName", op.display_name AS "operatorName",
               r.requirement_no AS "requirementNo", r.facts, r.objective, r.requested_service AS "requestedService",
               r.event_time AS "eventTime", r.event_location AS "eventLocation",
               r.service_days AS "serviceDays", r.writer_count AS "writerCount", r.unit_price AS "unitPrice",
               r.estimated_amount AS "estimatedAmount", r.onsite_contact_name AS "onsiteContactName",
               r.onsite_contact_mobile AS "onsiteContactMobile", r.deliverable_requirement AS "deliverableRequirement",
               r.matching_preference AS "matchingPreference"
        FROM project p JOIN app_user cu ON cu.id=p.customer_id JOIN organization o ON o.id=p.organization_id
        JOIN customer_requirement r ON r.id=p.requirement_id LEFT JOIN app_user op ON op.id=p.owner_operator_id
         WHERE p.id=?
         """, projectId));
    detail.put("conference", one("""
        SELECT cp.id, cp.conference_no AS "conferenceNo", cp.conference_type AS "conferenceType",
               cp.conference_format AS "conferenceFormat", cp.theme, cp.event_time AS "eventTime",
               cp.event_location AS "eventLocation", cp.attendee_scale AS "conferenceScale",
               cp.media_goal AS "mediaGoal", cp.guest_plan AS "guestPlan",
               cp.agenda_plan AS "agendaPlan", cp.venue_plan AS "venuePlan",
               cp.media_direction AS "mediaDirection", cp.communication_goal AS "communicationGoal",
               cp.agenda_status AS "agendaStatus",
               cp.venue_status AS "venueStatus", cp.contact_name AS "contactName",
               cp.contact_mobile AS "contactMobile", cp.status
        FROM conference_project cp WHERE cp.project_id=?
        """, projectId));
    detail.put("conferenceWorkItems", jdbc.queryForList("""
        SELECT cwi.id, cwi.item_no AS "itemNo", cwi.sort_order AS "sortOrder", cwi.phase,
               cwi.title, cwi.detail,
               cwi.due_at AS "dueAt", cwi.status, cwi.note, cwi.updated_at AS "updatedAt",
               cwi.assigned_operator_id AS "assignedOperatorId", op.display_name AS "operatorName"
        FROM conference_work_item cwi
        JOIN conference_project cp ON cp.id=cwi.conference_project_id
        LEFT JOIN app_user op ON op.id=cwi.assigned_operator_id
        WHERE cp.project_id=?
        ORDER BY cwi.sort_order, cwi.id
        """, projectId));
    detail.put("conferenceMediaCandidates", jdbc.queryForList("""
        SELECT cmc.id, cmc.candidate_key AS "candidateKey", cmc.candidate_type AS "candidateType",
               cmc.external_media_id AS "mediaId", cmc.media_name AS "displayName",
               cmc.external_reporter_id AS "reporterId", cmc.reporter_name AS "reporterName",
               cmc.media_attribute AS "attribute", cmc.province, cmc.city,
               cmc.channel_form AS "channelForm", cmc.category,
               cmc.coverage_tags AS "coverageTags", cmc.operation_note AS "operationNote",
               cmc.fit_score AS "score", cmc.reporter_news_count AS "newsCount",
               cmc.media_fans_count AS "fansCount", cmc.logo_url AS "logoUrl",
               cmc.reporter_avatar_url AS "avatarUrl", cmc.status, cmc.note,
               cmc.selected_at AS "selectedAt", cmc.invited_at AS "invitedAt",
               cmc.responded_at AS "respondedAt", cmc.updated_at AS "updatedAt",
               op.display_name AS "operatorName"
        FROM conference_media_candidate cmc
        JOIN conference_project cp ON cp.id=cmc.conference_project_id
        LEFT JOIN app_user op ON op.id=cmc.managed_operator_id
        WHERE cp.project_id=?
        ORDER BY cmc.status, cmc.fit_score DESC NULLS LAST, cmc.selected_at DESC
        """, projectId));
    detail.put("serviceIntakeTasks", jdbc.queryForList("""
        SELECT intake_task_no AS "taskNo", service_type AS "serviceType", title,
               customer_visible_note AS "customerVisibleNote", status, completed_at AS "completedAt",
               updated_at AS "updatedAt"
        FROM service_intake_task WHERE project_id=? ORDER BY updated_at DESC
        """, projectId));
    detail.put("manuscripts", jdbc.queryForList("""
        SELECT m.id, m.manuscript_no AS "manuscriptNo", m.title, m.status,
               m.current_version_no AS "currentVersionNo", m.approved_version_id AS "approvedVersionId",
               m.updated_at AS "updatedAt"
        FROM manuscript m WHERE m.project_id=? ORDER BY m.updated_at DESC
        """, projectId));
    detail.put("versions", jdbc.queryForList("""
        SELECT v.id, v.version_no AS "versionNo", v.manuscript_id AS "manuscriptId", v.version_number AS "versionNumber",
               v.title, v.summary, v.content, v.change_note AS "changeNote", v.review_comment AS "reviewComment",
               v.status, v.created_at AS "createdAt", v.reviewed_at AS "reviewedAt",
               sourceProject.project_name AS "sourceProjectName",
               sourceManuscript.title AS "sourceManuscriptTitle"
        FROM manuscript_version v
        JOIN manuscript m ON m.id=v.manuscript_id
        LEFT JOIN manuscript sourceManuscript ON sourceManuscript.id=v.source_manuscript_id
        LEFT JOIN project sourceProject ON sourceProject.id=sourceManuscript.project_id
        WHERE m.project_id=? ORDER BY v.version_number DESC
        """, projectId));
    detail.put("tasks", taskRows(" WHERE t.project_id=?", projectId));
    detail.put("results", jdbc.queryForList("""
        SELECT r.id, r.result_no AS "resultNo", r.channel_name AS "channelName", r.title, r.url,
               r.published_at AS "publishedAt", r.verified_at AS "verifiedAt", r.status
        FROM result_link r WHERE r.project_id=? ORDER BY r.created_at DESC
        """, projectId));
    detail.put("monitoring", jdbc.queryForList("""
        SELECT monitoring_no AS "monitoringNo", monitored_at AS "monitoredAt", metric_name AS "metricName",
               metric_value AS "metricValue", metric_text AS "metricText", source_url AS "sourceUrl", status
        FROM monitoring_record WHERE project_id=? ORDER BY monitored_at DESC
        """, projectId));
    detail.put("settlements", jdbc.queryForList("""
        SELECT settlement_no AS "settlementNo", amount, paid_amount AS "paidAmount", currency,
               due_at AS "dueAt", paid_at AS "paidAt", status
        FROM settlement_order WHERE project_id=? ORDER BY created_at DESC
        """, projectId));
    detail.put("files", jdbc.queryForList("""
        SELECT file_no AS "fileNo", original_name AS "originalName",
               content_type AS "contentType", file_size AS "fileSize", created_at AS "createdAt"
        FROM file_asset WHERE project_id=? AND status='ACTIVE' ORDER BY created_at DESC
        """, projectId));
    return detail;
  }

  /**
   * Returns only projects already visible to the caller. The group is an activity reference, not
   * a combined order: every row remains a separately priced requirement and project.
   */
  public List<Map<String, Object>> activityProjects(AuthPrincipal user, Long projectId) {
    StringBuilder sql = new StringBuilder("""
        SELECT p.id AS "projectId", p.project_no AS "projectNo", p.project_name AS "projectName",
               p.status, r.requested_service AS "requestedService", r.event_time AS "eventTime",
               r.unit_price AS "unitPrice", r.estimated_amount AS "estimatedAmount",
               p.created_at AS "createdAt"
        FROM project p
        JOIN customer_requirement r ON r.id=p.requirement_id
        WHERE COALESCE(p.activity_root_project_id, p.id) = (
          SELECT COALESCE(root.activity_root_project_id, root.id) FROM project root WHERE root.id=?
        )
        """);
    List<Object> args = new ArrayList<>(List.of(projectId));
    args.addAll(projectScope(sql, user));
    sql.append(" ORDER BY CASE WHEN p.id=? THEN 0 ELSE 1 END, p.created_at ASC");
    args.add(projectId);
    return jdbc.queryForList(sql.toString(), args.toArray());
  }

  public Map<String, Object> manuscriptContext(Long manuscriptId, Long versionId) {
    return one("""
        SELECT m.id AS manuscript_id, m.project_id, m.status AS manuscript_status, m.approved_version_id,
               v.id AS version_id, v.status AS version_status, v.title
        FROM manuscript m JOIN manuscript_version v ON v.manuscript_id=m.id
        WHERE m.id=? AND v.id=?
        """, manuscriptId, versionId);
  }

  public String projectRequestedService(Long projectId) {
    List<String> values = jdbc.query(
        """
        SELECT r.requested_service
        FROM project p
        JOIN customer_requirement r ON r.id=p.requirement_id
        WHERE p.id=?
        """,
        (rs, rowNum) -> rs.getString(1), projectId);
    return values.isEmpty() ? null : values.get(0);
  }

  public Map<String, Object> lockProjectForManuscriptSubmission(Long projectId) {
    return one("""
        SELECT r.requested_service AS "requestedService",
               (
                 SELECT m.status
                 FROM manuscript m
                 WHERE m.project_id=p.id
                 ORDER BY m.updated_at DESC, m.id DESC
                 LIMIT 1
               ) AS "manuscriptStatus"
        FROM project p
        JOIN customer_requirement r ON r.id=p.requirement_id
        WHERE p.id=?
        FOR UPDATE OF p
        """, projectId);
  }

  /** Returns safe labels for the current customer's eligible source versions only. */
  public List<Map<String, Object>> approvedCustomerManuscriptSources(AuthPrincipal user) {
    return jdbc.queryForList("""
        SELECT m.id AS "manuscriptId", v.id AS "versionId", p.id AS "projectId",
               p.project_no AS "projectNo", p.project_name AS "projectName",
               v.title, v.version_number AS "versionNumber", v.reviewed_at AS "confirmedAt"
        FROM manuscript m
        JOIN manuscript_version v ON v.id=m.approved_version_id
        JOIN project p ON p.id=m.project_id
        WHERE p.customer_id=? AND p.organization_id=?
          AND v.status='APPROVED'
          AND p.status NOT IN ('CANCELLED', 'ARCHIVED')
        ORDER BY v.reviewed_at DESC NULLS LAST, v.created_at DESC
        """, user.userId(), user.organizationId());
  }

  /**
   * Looks up a source version within the customer's own organization. It intentionally includes
   * content for server-side copying only and must never be sent from the source-list endpoint.
   */
  public Map<String, Object> approvedCustomerManuscriptSource(
      AuthPrincipal user, Long manuscriptId, Long versionId) {
    return one("""
        SELECT m.id AS "manuscriptId", v.id AS "versionId", p.id AS "projectId",
               v.title, v.summary, v.content, v.change_note AS "changeNote"
        FROM manuscript m
        JOIN manuscript_version v ON v.id=m.approved_version_id
        JOIN project p ON p.id=m.project_id
        WHERE m.id=? AND v.id=? AND v.status='APPROVED'
          AND p.customer_id=? AND p.organization_id=?
          AND p.status NOT IN ('CANCELLED', 'ARCHIVED')
        """, manuscriptId, versionId, user.userId(), user.organizationId());
  }

  @Transactional
  public Long submitManuscript(AuthPrincipal user, Long projectId, SubmitManuscriptRequest request) {
    List<Map<String, Object>> existing = jdbc.queryForList("SELECT id, current_version_no FROM manuscript WHERE project_id=? ORDER BY id LIMIT 1", projectId);
    Long manuscriptId;
    int versionNumber;
    if (existing.isEmpty()) {
      KeyHolder manuscriptKey = new GeneratedKeyHolder();
      jdbc.update(connection -> {
        PreparedStatement ps = connection.prepareStatement("""
            INSERT INTO manuscript (manuscript_no, project_id, title, current_version_no, status)
            VALUES (?, ?, ?, 1, 'CLIENT_REVIEW')
            """, Statement.RETURN_GENERATED_KEYS);
        ps.setString(1, no("MAN")); ps.setLong(2, projectId); ps.setString(3, request.title()); return ps;
      }, manuscriptKey);
      manuscriptId = key(manuscriptKey);
      versionNumber = 1;
    } else {
      manuscriptId = ((Number) existing.get(0).get("id")).longValue();
      versionNumber = ((Number) existing.get(0).get("current_version_no")).intValue() + 1;
      jdbc.update("UPDATE manuscript SET title=?, current_version_no=?, status='CLIENT_REVIEW' WHERE id=?",
          request.title(), versionNumber, manuscriptId);
    }
    KeyHolder versionKey = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO manuscript_version
          (version_no, manuscript_id, version_number, title, summary, content, change_note, submitted_by, status)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'CLIENT_REVIEW')
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, no("VER")); ps.setLong(2, manuscriptId); ps.setInt(3, versionNumber);
      ps.setString(4, request.title()); ps.setString(5, request.summary()); ps.setString(6, request.content());
      ps.setString(7, request.changeNote()); ps.setLong(8, user.userId()); return ps;
    }, versionKey);
    jdbc.update("UPDATE editorial_task SET status='COMPLETED' WHERE project_id=?", projectId);
    jdbc.update("""
        UPDATE writing_assignment_member member
        SET status='COMPLETED'
        FROM writing_assignment assignment_record, editorial_task task
        WHERE assignment_record.editorial_task_id=task.id
          AND member.assignment_id=assignment_record.id
          AND task.project_id=?
          AND member.status='ACCEPTED'
        """, projectId);
    jdbc.update("""
        UPDATE writing_assignment wa
        SET status='COMPLETED'
        FROM editorial_task et
        WHERE wa.editorial_task_id=et.id AND et.project_id=?
          AND wa.status='ACCEPTED'
        """, projectId);
    jdbc.update("UPDATE project SET status='CLIENT_REVIEW' WHERE id=?", projectId);
    log(user, "SUBMIT_MANUSCRIPT", "MANUSCRIPT", String.valueOf(manuscriptId), Map.of("versionNumber", versionNumber));
    return manuscriptId;
  }

  /**
   * Copies an already approved customer version into the newly created direct-publishing project.
   * It does not share the manuscript row or change the source project; the stored provenance is
   * strictly for audit and customer-facing traceability.
   */
  @Transactional
  public Long copyApprovedManuscriptToDirectProject(
      AuthPrincipal user, Long projectId, Long sourceManuscriptId, Long sourceVersionId) {
    Map<String, Object> source = approvedCustomerManuscriptSource(
        user, sourceManuscriptId, sourceVersionId);
    if (source.isEmpty()) return null;
    KeyHolder manuscriptKey = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO manuscript (manuscript_no, project_id, title, current_version_no, status)
          VALUES (?, ?, ?, 1, 'CLIENT_APPROVED')
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, no("MAN"));
      ps.setLong(2, projectId);
      ps.setString(3, String.valueOf(source.get("title")));
      return ps;
    }, manuscriptKey);
    Long copiedManuscriptId = key(manuscriptKey);
    KeyHolder versionKey = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO manuscript_version
          (version_no, manuscript_id, version_number, title, summary, content, change_note,
           submitted_by, status, reviewed_by, reviewed_at, review_comment,
           source_manuscript_id, source_version_id)
          VALUES (?, ?, 1, ?, ?, ?, ?, ?, 'APPROVED', ?, CURRENT_TIMESTAMP, ?, ?, ?)
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, no("VER"));
      ps.setLong(2, copiedManuscriptId);
      ps.setString(3, String.valueOf(source.get("title")));
      ps.setString(4, source.get("summary") == null ? null : String.valueOf(source.get("summary")));
      ps.setString(5, String.valueOf(source.get("content")));
      ps.setString(6, source.get("changeNote") == null ? "使用客户已确认稿件" : String.valueOf(source.get("changeNote")));
      ps.setLong(7, user.userId());
      ps.setLong(8, user.userId());
      ps.setString(9, "客户选择已确认稿件");
      ps.setLong(10, sourceManuscriptId);
      ps.setLong(11, sourceVersionId);
      return ps;
    }, versionKey);
    Long copiedVersionId = key(versionKey);
    jdbc.update("UPDATE manuscript SET approved_version_id=? WHERE id=?", copiedVersionId, copiedManuscriptId);
    log(user, "COPY_APPROVED_MANUSCRIPT_TO_DIRECT_PROJECT", "MANUSCRIPT",
        String.valueOf(copiedManuscriptId), Map.of(
            "projectId", projectId,
            "sourceProjectId", source.get("projectId"),
            "sourceManuscriptId", sourceManuscriptId,
            "sourceVersionId", sourceVersionId));
    return copiedManuscriptId;
  }

  /**
   * Persists a customer-provided final version for a direct-publishing project. The version is
   * explicitly approved at submission time because the customer is the confirming party; it is
   * still fully versioned and audited before any channel plan can be created.
   */
  @Transactional
  public Long submitCustomerApprovedManuscript(
      AuthPrincipal user, Long projectId, SubmitManuscriptRequest request) {
    List<Map<String, Object>> existing = jdbc.queryForList(
        "SELECT id, current_version_no FROM manuscript WHERE project_id=? ORDER BY id LIMIT 1", projectId);
    Long manuscriptId;
    int versionNumber;
    if (existing.isEmpty()) {
      KeyHolder manuscriptKey = new GeneratedKeyHolder();
      jdbc.update(connection -> {
        PreparedStatement ps = connection.prepareStatement("""
            INSERT INTO manuscript (manuscript_no, project_id, title, current_version_no, status)
            VALUES (?, ?, ?, 1, 'CLIENT_APPROVED')
            """, Statement.RETURN_GENERATED_KEYS);
        ps.setString(1, no("MAN"));
        ps.setLong(2, projectId);
        ps.setString(3, request.title());
        return ps;
      }, manuscriptKey);
      manuscriptId = key(manuscriptKey);
      versionNumber = 1;
    } else {
      manuscriptId = ((Number) existing.get(0).get("id")).longValue();
      versionNumber = ((Number) existing.get(0).get("current_version_no")).intValue() + 1;
      jdbc.update(
          "UPDATE manuscript SET title=?, current_version_no=?, status='CLIENT_APPROVED' WHERE id=?",
          request.title(), versionNumber, manuscriptId);
    }
    KeyHolder versionKey = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO manuscript_version
          (version_no, manuscript_id, version_number, title, summary, content, change_note,
           submitted_by, status, reviewed_by, reviewed_at, review_comment)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'APPROVED', ?, CURRENT_TIMESTAMP, ?)
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, no("VER"));
      ps.setLong(2, manuscriptId);
      ps.setInt(3, versionNumber);
      ps.setString(4, request.title());
      ps.setString(5, request.summary());
      ps.setString(6, request.content());
      ps.setString(7, request.changeNote());
      ps.setLong(8, user.userId());
      ps.setLong(9, user.userId());
      ps.setString(10, "客户提交并确认");
      return ps;
    }, versionKey);
    Long versionId = key(versionKey);
    jdbc.update("UPDATE manuscript SET approved_version_id=? WHERE id=?", versionId, manuscriptId);
    log(user, "SUBMIT_CUSTOMER_APPROVED_MANUSCRIPT", "MANUSCRIPT", String.valueOf(manuscriptId),
        Map.of("versionNumber", versionNumber, "projectId", projectId));
    return manuscriptId;
  }

  @Transactional
  public void reviewManuscript(AuthPrincipal user, Long manuscriptId, Long versionId, String decision, String comment) {
    String versionStatus = "APPROVE".equals(decision) ? "APPROVED" : "CLIENT_RETURNED";
    jdbc.update("""
        UPDATE manuscript_version SET status=?, reviewed_by=?, reviewed_at=CURRENT_TIMESTAMP, review_comment=?
        WHERE id=? AND manuscript_id=?
        """, versionStatus, user.userId(), comment, versionId, manuscriptId);
    if ("APPROVE".equals(decision)) {
      jdbc.update("UPDATE manuscript SET status='CLIENT_APPROVED', approved_version_id=? WHERE id=?", versionId, manuscriptId);
      jdbc.update("""
          UPDATE project p
          SET status='COMPLETED'
          FROM manuscript m, customer_requirement r
          WHERE m.id=? AND p.id=m.project_id AND r.id=p.requirement_id
            AND r.requested_service='ONSITE_WRITING'
          """, manuscriptId);
    } else {
      jdbc.update("UPDATE manuscript SET status='CLIENT_RETURNED' WHERE id=?", manuscriptId);
      jdbc.update("""
          UPDATE project p
          SET status='IN_PROGRESS'
          FROM manuscript m, customer_requirement r
          WHERE m.id=? AND p.id=m.project_id AND r.id=p.requirement_id
            AND r.requested_service='ONSITE_WRITING'
          """, manuscriptId);
      jdbc.update("""
          UPDATE editorial_task et
          SET status='PENDING_EXECUTION'
          FROM manuscript m, project p, customer_requirement r
          WHERE m.id=? AND p.id=m.project_id AND r.id=p.requirement_id
            AND r.requested_service='ONSITE_WRITING' AND et.project_id=p.id
          """, manuscriptId);
      jdbc.update("""
          UPDATE writing_assignment_member member
          SET status='ACCEPTED'
          FROM writing_assignment assignment_record, editorial_task et, manuscript m, project p,
               customer_requirement r
          WHERE m.id=? AND p.id=m.project_id AND r.id=p.requirement_id
            AND r.requested_service='ONSITE_WRITING'
            AND et.project_id=p.id AND assignment_record.editorial_task_id=et.id
            AND member.assignment_id=assignment_record.id
            AND member.status='COMPLETED'
          """, manuscriptId);
      jdbc.update("""
          UPDATE writing_assignment wa
          SET status='ACCEPTED'
          FROM editorial_task et, manuscript m, project p, customer_requirement r
          WHERE m.id=? AND p.id=m.project_id AND r.id=p.requirement_id
            AND r.requested_service='ONSITE_WRITING'
            AND et.project_id=p.id AND wa.editorial_task_id=et.id
            AND wa.status='COMPLETED'
          """, manuscriptId);
    }
    log(user, "APPROVE".equals(decision) ? "APPROVE_MANUSCRIPT" : "RETURN_MANUSCRIPT",
        "MANUSCRIPT", String.valueOf(manuscriptId), Map.of("versionId", versionId, "comment", comment == null ? "" : comment));
  }

  public List<Map<String, Object>> channels(
      String type, String keyword, String region, String category, String publishForm,
      BigDecimal minPrice, BigDecimal maxPrice, Integer maxDays, Boolean linkSupport,
      String linkType, String newsSource, String entryLevel, String specialIndustry,
      String weekendPolicy,
      String sort, int limit, int offset) {
    StringBuilder sql = new StringBuilder("""
        SELECT c.id, c.channel_name AS "channelName", c.channel_type AS "channelType",
               c.category, c.region, c.publish_form AS "publishForm", c.expected_days AS "expectedDays",
               c.link_support AS "linkSupport", c.public_notes AS "publicNotes", c.status,
               substring(c.public_notes from '链接类型：([^；]+)') AS "linkType",
               substring(c.public_notes from '新闻源：([^；]+)') AS "newsSource",
               substring(c.public_notes from '入口级别：([^；]+)') AS "entryLevel",
               substring(c.public_notes from '特殊行业：([^；]+)') AS "specialIndustry",
               CASE
                 WHEN c.public_notes LIKE '%周末可沟通发布%' THEN '可沟通发布'
                 WHEN c.public_notes LIKE '%周末发布需提交后复核%' THEN '发布需提交后复核'
               END AS "weekendPolicy",
               q.customer_price AS "customerPrice", q.currency,
               q.valid_until AS "validUntil", q.public_terms AS "publicTerms"
        FROM publish_channel c
        LEFT JOIN LATERAL (
          SELECT id, customer_price, currency, valid_until, public_terms FROM channel_quote
          WHERE channel_id=c.id AND status='ACTIVE' AND valid_until>CURRENT_TIMESTAMP
          ORDER BY valid_until DESC LIMIT 1
        ) q ON TRUE
        WHERE c.status='ACTIVE'
        """);
    List<Object> args = new ArrayList<>();
    appendPublicChannelFilters(
        sql, args, type, keyword, region, category, publishForm, minPrice, maxPrice,
        maxDays, linkSupport, linkType, newsSource, entryLevel, specialIndustry, weekendPolicy);
    sql.append(" ORDER BY ");
    switch (sort) {
      case "PRICE_DESC" -> sql.append("q.customer_price DESC NULLS LAST, c.channel_name");
      case "DELIVERY_ASC" -> sql.append("c.expected_days ASC NULLS LAST, q.customer_price NULLS LAST, c.channel_name");
      case "NAME_ASC" -> sql.append("c.channel_name ASC");
      default -> sql.append("q.customer_price ASC NULLS LAST, c.expected_days ASC NULLS LAST, c.channel_name");
    }
    sql.append(" LIMIT ? OFFSET ?"); args.add(limit); args.add(offset);
    return jdbc.queryForList(sql.toString(), args.toArray());
  }

  public long channelsCount(
      String type, String keyword, String region, String category, String publishForm,
      BigDecimal minPrice, BigDecimal maxPrice, Integer maxDays, Boolean linkSupport,
      String linkType, String newsSource, String entryLevel, String specialIndustry,
      String weekendPolicy) {
    StringBuilder sql = new StringBuilder("""
        SELECT count(*) FROM publish_channel c
        LEFT JOIN LATERAL (
          SELECT id, customer_price, valid_until FROM channel_quote
          WHERE channel_id=c.id AND status='ACTIVE' AND valid_until>CURRENT_TIMESTAMP
          ORDER BY valid_until DESC LIMIT 1
        ) q ON TRUE
        WHERE c.status='ACTIVE'
        """);
    List<Object> args = new ArrayList<>();
    appendPublicChannelFilters(
        sql, args, type, keyword, region, category, publishForm, minPrice, maxPrice,
        maxDays, linkSupport, linkType, newsSource, entryLevel, specialIndustry, weekendPolicy);
    return count(sql.toString(), args.toArray());
  }

  public Map<String, Object> channelTaxonomy(String type) {
    Map<String, Object> taxonomy = new LinkedHashMap<>();
    taxonomy.put("regions", distinctChannelValues("region", type));
    taxonomy.put("categories", distinctChannelValues("category", type));
    taxonomy.put("publishForms", distinctChannelValues("publish_form", type));
    taxonomy.put("linkTypes", distinctChannelNoteValues("link_type", type));
    taxonomy.put("newsSources", distinctChannelNoteValues("news_source", type));
    taxonomy.put("entryLevels", distinctChannelNoteValues("entry_level", type));
    taxonomy.put("specialIndustries", distinctChannelNoteValues("special_industry", type));
    taxonomy.put("weekendPolicies", distinctChannelNoteValues("weekend_policy", type));
    return taxonomy;
  }

  private List<String> distinctChannelValues(String field, String type) {
    String safeField = switch (field) {
      case "region" -> "region";
      case "category" -> "category";
      case "publish_form" -> "publish_form";
      default -> throw new IllegalArgumentException("unsupported channel taxonomy field");
    };
    StringBuilder sql = new StringBuilder(
        "SELECT DISTINCT btrim(c." + safeField + ") AS value FROM publish_channel c " +
        "WHERE c.status='ACTIVE' AND c." + safeField + " IS NOT NULL AND btrim(c." + safeField + ")<>''");
    List<Object> args = new ArrayList<>();
    if (type != null && !type.isBlank()) {
      sql.append(" AND c.channel_type=?");
      args.add(type);
    }
    if ("DIRECT_PUBLISHING".equals(type)) {
      sql.append("""
           AND EXISTS (
             SELECT 1 FROM channel_quote q
             WHERE q.channel_id=c.id AND q.status='ACTIVE' AND q.valid_until>CURRENT_TIMESTAMP
           )
          """);
    }
    sql.append(" ORDER BY value");
    return jdbc.query(sql.toString(), (rs, rowNum) -> rs.getString("value"), args.toArray());
  }

  private List<String> distinctChannelNoteValues(String field, String type) {
    String expression = channelNoteExpression(field);
    StringBuilder sql = new StringBuilder(
        "SELECT DISTINCT btrim(" + expression + ") AS value FROM publish_channel c " +
        "WHERE c.status='ACTIVE' AND " + expression + " IS NOT NULL " +
        "AND btrim(" + expression + ")<>''");
    List<Object> args = new ArrayList<>();
    if (type != null && !type.isBlank()) {
      sql.append(" AND c.channel_type=?");
      args.add(type);
    }
    if ("DIRECT_PUBLISHING".equals(type)) {
      sql.append("""
           AND EXISTS (
             SELECT 1 FROM channel_quote q
             WHERE q.channel_id=c.id AND q.status='ACTIVE' AND q.valid_until>CURRENT_TIMESTAMP
           )
          """);
    }
    sql.append(" ORDER BY value");
    return jdbc.query(sql.toString(), (rs, rowNum) -> rs.getString("value"), args.toArray());
  }

  private String channelNoteExpression(String field) {
    return switch (field) {
      case "link_type" -> "substring(c.public_notes from '链接类型：([^；]+)')";
      case "news_source" -> "substring(c.public_notes from '新闻源：([^；]+)')";
      case "entry_level" -> "substring(c.public_notes from '入口级别：([^；]+)')";
      case "special_industry" -> "substring(c.public_notes from '特殊行业：([^；]+)')";
      case "weekend_policy" ->
          """
          CASE
            WHEN c.public_notes LIKE '%周末可沟通发布%' THEN '可沟通发布'
            WHEN c.public_notes LIKE '%周末发布需提交后复核%' THEN '发布需提交后复核'
          END
          """;
      default -> throw new IllegalArgumentException("unsupported channel note taxonomy field");
    };
  }

  private void appendPublicChannelFilters(
      StringBuilder sql, List<Object> args, String type, String keyword, String region, String category,
      String publishForm, BigDecimal minPrice, BigDecimal maxPrice, Integer maxDays,
      Boolean linkSupport, String linkType, String newsSource, String entryLevel,
      String specialIndustry, String weekendPolicy) {
    if (type != null && !type.isBlank()) { sql.append(" AND c.channel_type=?"); args.add(type); }
    if ("DIRECT_PUBLISHING".equals(type)) sql.append(" AND q.id IS NOT NULL");
    if (region != null && !region.isBlank()) { sql.append(" AND c.region ILIKE ?"); args.add("%" + region.trim() + "%"); }
    if (category != null && !category.isBlank()) { sql.append(" AND c.category ILIKE ?"); args.add("%" + category.trim() + "%"); }
    if (publishForm != null && !publishForm.isBlank()) { sql.append(" AND c.publish_form ILIKE ?"); args.add("%" + publishForm.trim() + "%"); }
    if (keyword != null && !keyword.isBlank()) {
      sql.append(" AND (c.channel_name ILIKE ? OR c.channel_no ILIKE ? OR c.category ILIKE ?)");
      String value = "%" + keyword.trim() + "%";
      args.add(value); args.add(value); args.add(value);
    }
    if (minPrice != null) { sql.append(" AND q.customer_price>=?"); args.add(minPrice); }
    if (maxPrice != null) { sql.append(" AND q.customer_price<=?"); args.add(maxPrice); }
    if (maxDays != null) { sql.append(" AND c.expected_days<=?"); args.add(maxDays); }
    if (linkSupport != null) { sql.append(" AND c.link_support=?"); args.add(linkSupport); }
    appendChannelNoteFilter(sql, args, "link_type", linkType);
    appendChannelNoteFilter(sql, args, "news_source", newsSource);
    appendChannelNoteFilter(sql, args, "entry_level", entryLevel);
    appendChannelNoteFilter(sql, args, "special_industry", specialIndustry);
    appendChannelNoteFilter(sql, args, "weekend_policy", weekendPolicy);
  }

  private void appendChannelNoteFilter(
      StringBuilder sql, List<Object> args, String field, String value) {
    if (value == null || value.isBlank()) return;
    sql.append(" AND ").append(channelNoteExpression(field)).append("=?");
    args.add(value.trim());
  }

  public Map<String, Object> channel(Long channelId) {
    return one("""
        SELECT c.*, q.id AS quote_id, q.customer_price, q.cost_price, q.valid_until, q.public_terms
        FROM publish_channel c
        LEFT JOIN LATERAL (SELECT * FROM channel_quote WHERE channel_id=c.id AND status='ACTIVE'
          AND valid_until>CURRENT_TIMESTAMP ORDER BY valid_until DESC LIMIT 1) q ON TRUE
        WHERE c.id=?
        """, channelId);
  }

  public boolean hasActiveLock(Long manuscriptId) {
    releaseExpiredLocks(manuscriptId);
    return count("""
        SELECT count(*) FROM manuscript_lock
        WHERE manuscript_id=? AND active=TRUE AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP)
        """, manuscriptId) > 0;
  }

  public void createMediaPrLock(
      AuthPrincipal user, Long manuscriptId, Long manuscriptVersionId, OffsetDateTime expiresAt) {
    releaseExpiredLocks(manuscriptId);
    jdbc.update("""
        INSERT INTO manuscript_lock
        (lock_no, manuscript_id, manuscript_version_id, locked_by, reason, active, expires_at, status)
        VALUES (?, ?, ?, ?, '已确认媒体邀约排他安排，锁定当前定稿版本', TRUE, ?, 'ACTIVE')
        """, no("LCK"), manuscriptId, manuscriptVersionId, user.userId(), expiresAt);
  }

  private void releaseExpiredLocks(Long manuscriptId) {
    jdbc.update("""
        UPDATE manuscript_lock
        SET active=FALSE, released_at=COALESCE(released_at, CURRENT_TIMESTAMP), status='EXPIRED'
        WHERE manuscript_id=? AND active=TRUE AND expires_at IS NOT NULL AND expires_at<=CURRENT_TIMESTAMP
        """, manuscriptId);
  }

  public void releaseLock(Long manuscriptId) {
    jdbc.update("""
        UPDATE manuscript_lock SET active=FALSE, released_at=CURRENT_TIMESTAMP, status='RELEASED'
        WHERE manuscript_id=? AND active=TRUE
        """, manuscriptId);
  }

  @Transactional
  public Map<String, Object> existingPublishPlan(
      AuthPrincipal user, Long projectId, String submissionKey, String submissionHash) {
    String lockKey =
        user.userId() + ":" + user.organizationId() + ":" + projectId + ":" + submissionKey;
    jdbc.queryForObject(
        "SELECT pg_advisory_xact_lock(hashtextextended(?, 0)), 1",
        (rs, rowNum) -> rs.getInt(2),
        lockKey);
    List<Map<String, Object>> existing = jdbc.queryForList("""
        SELECT pp.plan_no AS "planNo", pp.submission_hash AS "submissionHash",
               pp.status, pp.estimated_amount AS "estimatedAmount",
               count(ppi.id)::INT AS "itemCount"
        FROM publish_plan pp
        LEFT JOIN publish_plan_item ppi ON ppi.publish_plan_id=pp.id
        WHERE pp.project_id=? AND pp.created_by=? AND pp.submission_key=?
        GROUP BY pp.id
        """, projectId, user.userId(), submissionKey);
    if (existing.isEmpty()) return Map.of();
    Map<String, Object> plan = existing.get(0);
    if (!submissionHash.equals(String.valueOf(plan.get("submissionHash")))) {
      throw new BusinessException(
          "IDEMPOTENCY_KEY_REUSED",
          "本次请求标识已用于另一份发布计划，请刷新页面后重新提交",
          HttpStatus.CONFLICT);
    }
    return plan;
  }

  @Transactional
  public Map<String, Object> createPublishPlan(
      AuthPrincipal user, Long projectId, Long manuscriptId, Long manuscriptVersionId,
      String planName, String objective, boolean exclusiveMediaPr, OffsetDateTime lockExpiresAt,
      List<Map<String, Object>> channelRows, List<ChannelSelection> selections,
      String submissionKey, String submissionHash) {
    BigDecimal estimatedAmount = BigDecimal.ZERO;
    for (Map<String, Object> channel : channelRows) {
      if (channel.get("customer_price") instanceof BigDecimal price) estimatedAmount = estimatedAmount.add(price);
    }
    String planNo = no("PLAN");
    KeyHolder planKey = new GeneratedKeyHolder();
    BigDecimal finalEstimatedAmount = estimatedAmount;
    jdbc.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO publish_plan
          (plan_no, project_id, manuscript_id, manuscript_version_id, plan_name, objective,
           estimated_amount, currency, exclusive_media_pr, lock_expires_at, created_by,
           submission_key, submission_hash, status)
          VALUES (?, ?, ?, ?, ?, ?, ?, 'CNY', ?, ?, ?, ?, ?, 'WAITING_CONFIRMATION')
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, planNo);
      ps.setLong(2, projectId);
      ps.setObject(3, manuscriptId, java.sql.Types.BIGINT);
      ps.setObject(4, manuscriptVersionId, java.sql.Types.BIGINT);
      ps.setString(5, planName);
      ps.setString(6, objective);
      ps.setBigDecimal(7, finalEstimatedAmount);
      ps.setBoolean(8, exclusiveMediaPr);
      ps.setObject(9, lockExpiresAt);
      ps.setLong(10, user.userId());
      ps.setString(11, submissionKey);
      ps.setString(12, submissionHash);
      return ps;
    }, planKey);
    Long planId = key(planKey);
    for (int i = 0; i < selections.size(); i++) {
      ChannelSelection selection = selections.get(i);
      Map<String, Object> channel = channelRows.get(i);
      jdbc.update("""
          INSERT INTO publish_plan_item
          (item_no, publish_plan_id, channel_id, channel_type, planned_publish_at, journalist_name,
           media_name, note, media_candidate_json, quote_id, unit_price_snapshot, price_valid_until, status)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, 'DRAFT')
          """, no("PLAN-ITM"), planId, selection.channelId(), channel.get("channel_type"),
          selection.plannedPublishAt(), selection.journalistName(), selection.mediaName(), selection.note(),
          toJson(selection.mediaCandidate()), channel.get("quote_id"), channel.get("customer_price"),
          channel.get("valid_until"));
    }
    log(user, "CREATE_PUBLISH_PLAN", "PUBLISH_PLAN", planNo,
        Map.of("projectId", projectId, "itemCount", selections.size(), "exclusiveMediaPr", exclusiveMediaPr));
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("planNo", planNo);
    result.put("status", "WAITING_CONFIRMATION");
    result.put("itemCount", selections.size());
    result.put("estimatedAmount", estimatedAmount);
    return result;
  }

  public List<Map<String, Object>> publishPlans(Long projectId) {
    return jdbc.queryForList("""
        SELECT pp.id, pp.plan_no AS "planNo", pp.project_id AS "projectId", pp.plan_name AS "planName",
               pp.estimated_amount AS "estimatedAmount", pp.currency, pp.exclusive_media_pr AS "exclusiveMediaPr",
               pp.lock_expires_at AS "lockExpiresAt", pp.status, pp.confirmed_at AS "confirmedAt",
               pp.created_at AS "createdAt", count(ppi.id) AS "itemCount"
        FROM publish_plan pp LEFT JOIN publish_plan_item ppi ON ppi.publish_plan_id=pp.id
        WHERE pp.project_id=?
        GROUP BY pp.id ORDER BY pp.created_at DESC
        """, projectId);
  }

  /**
   * Customer-facing plan history is limited to plans whose every item belongs to the project's
   * independent service. Historical cross-service rows remain available to platform operators
   * for audit, but cannot re-enter a customer's current project workflow.
   */
  public List<Map<String, Object>> publishPlansForService(Long projectId, String serviceType) {
    return jdbc.queryForList("""
        SELECT pp.id, pp.plan_no AS "planNo", pp.project_id AS "projectId", pp.plan_name AS "planName",
               pp.estimated_amount AS "estimatedAmount", pp.currency, pp.exclusive_media_pr AS "exclusiveMediaPr",
               pp.lock_expires_at AS "lockExpiresAt", pp.status, pp.confirmed_at AS "confirmedAt",
               pp.created_at AS "createdAt",
               (SELECT count(*) FROM publish_plan_item item
                WHERE item.publish_plan_id=pp.id) AS "itemCount"
        FROM publish_plan pp
        WHERE pp.project_id=?
          AND EXISTS (
            SELECT 1 FROM publish_plan_item item
            WHERE item.publish_plan_id=pp.id
          )
          AND NOT EXISTS (
            SELECT 1 FROM publish_plan_item item
            WHERE item.publish_plan_id=pp.id AND item.channel_type<>?
          )
        ORDER BY pp.created_at DESC
        """, projectId, serviceType);
  }

  public Map<String, Object> lockPublishPlanForUpdateByNo(String planNo) {
    return one("""
        SELECT pp.id, pp.plan_no AS "planNo", pp.project_id AS "projectId",
               pp.manuscript_id AS "manuscriptId", pp.manuscript_version_id AS "manuscriptVersionId",
               pp.exclusive_media_pr AS "exclusiveMediaPr", pp.lock_expires_at AS "lockExpiresAt",
               pp.status, p.customer_id AS "customerId"
        FROM publish_plan pp JOIN project p ON p.id=pp.project_id
        WHERE pp.plan_no=? FOR UPDATE
        """, planNo);
  }

  public List<Map<String, Object>> publishPlanItems(Long planId) {
    return jdbc.queryForList("""
        SELECT ppi.id AS "planItemId", pp.id AS "publishPlanId",
               pp.project_id AS "projectId", pp.manuscript_id AS "manuscriptId",
               pp.manuscript_version_id AS "manuscriptVersionId", ppi.channel_id AS "channelId",
               ppi.channel_type AS "channelType", ppi.planned_publish_at AS "plannedPublishAt",
               ppi.journalist_name AS "journalistName", ppi.media_name AS "mediaName", ppi.note,
               ppi.quote_id AS "quoteId", ppi.unit_price_snapshot AS "unitPriceSnapshot",
               ppi.price_valid_until AS "priceValidUntil", ppi.status AS "itemStatus",
               CASE
                 WHEN ppi.channel_type='MEDIA_PR' AND ppi.channel_id IS NULL THEN 'MANUAL_REVIEW'
                 ELSE c.status
               END AS "channelStatus", m.title AS "manuscriptTitle",
               q.cost_price AS "costPriceSnapshot",
               COALESCE(q.supplier_id, sc.supplier_id) AS "supplierId",
               q.status AS "quoteStatus", q.valid_until AS "currentQuoteValidUntil",
               (q.id IS NOT NULL AND q.status='ACTIVE' AND q.valid_until>CURRENT_TIMESTAMP
                AND ppi.price_valid_until>CURRENT_TIMESTAMP) AS "quoteUsable",
               ppi.media_candidate_json->>'candidateType' AS "candidateType",
               ppi.media_candidate_json->>'candidateKey' AS "candidateKey",
               ppi.media_candidate_json->>'mediaId' AS "externalMediaId",
               ppi.media_candidate_json->>'reporterId' AS "externalReporterId",
               ppi.media_candidate_json->>'reporterName' AS "reporterName",
               ppi.media_candidate_json->>'attribute' AS "mediaAttribute",
               ppi.media_candidate_json->>'province' AS "mediaProvince",
               ppi.media_candidate_json->>'city' AS "mediaCity",
               ppi.media_candidate_json->>'channelForm' AS "mediaChannelForm",
               ppi.media_candidate_json->>'category' AS "mediaCategory",
               NULLIF(ppi.media_candidate_json->>'score','')::NUMERIC AS "mediaScore"
        FROM publish_plan_item ppi
        JOIN publish_plan pp ON pp.id=ppi.publish_plan_id
        LEFT JOIN publish_channel c ON c.id=ppi.channel_id
        LEFT JOIN manuscript m ON m.id=pp.manuscript_id
        LEFT JOIN channel_quote q ON q.id=ppi.quote_id
        LEFT JOIN LATERAL (
          SELECT mapping.supplier_id
          FROM supplier_channel mapping
          JOIN supplier s ON s.id=mapping.supplier_id AND s.status='ACTIVE'
          WHERE mapping.channel_id=ppi.channel_id AND mapping.status='ACTIVE'
          ORDER BY mapping.priority, mapping.id
          LIMIT 1
        ) sc ON TRUE
        WHERE ppi.publish_plan_id=? ORDER BY ppi.id
        """, planId);
  }

  public List<String> publishPlanTaskNos(Long planId) {
    return jdbc.queryForList("""
        SELECT t.task_no FROM publish_task t
        JOIN publish_plan_item ppi ON ppi.id=t.publish_plan_item_id
        WHERE ppi.publish_plan_id=? ORDER BY t.task_no
        """, String.class, planId);
  }

  public Long createPublishTaskFromPlan(AuthPrincipal user, Map<String, Object> item) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    String taskNo = no("PUB");
    Long channelId = item.get("channelId") == null ? null : number(item.get("channelId"));
    jdbc.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO publish_task
          (task_no, publish_plan_item_id, project_id, manuscript_id, manuscript_version_id,
           channel_id, channel_type, planned_publish_at, execution_note, status)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_ASSIGNMENT')
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, taskNo);
      ps.setLong(2, number(item.get("planItemId")));
      ps.setLong(3, number(item.get("projectId")));
      ps.setObject(4, item.get("manuscriptId"), java.sql.Types.BIGINT);
      ps.setObject(5, item.get("manuscriptVersionId"), java.sql.Types.BIGINT);
      ps.setObject(6, channelId, java.sql.Types.BIGINT);
      ps.setString(7, String.valueOf(item.get("channelType")));
      ps.setObject(8, item.get("plannedPublishAt"));
      ps.setString(9, item.get("note") == null ? null : String.valueOf(item.get("note")));
      return ps;
    }, keyHolder);
    Long taskId = key(keyHolder);
    jdbc.update("""
        UPDATE publish_task t SET assigned_operator_id=p.owner_operator_id,
          status=CASE WHEN p.owner_operator_id IS NULL THEN 'PENDING_ASSIGNMENT' ELSE 'PENDING_EXECUTION' END
        FROM project p WHERE t.id=? AND p.id=t.project_id
        """, taskId);
    String type = String.valueOf(item.get("channelType"));
    if ("MEDIA_PR".equals(type)) {
      jdbc.update("""
          INSERT INTO media_pr_invitation
          (invitation_no, publish_task_id, candidate_type, journalist_name, media_name,
           external_media_id, external_reporter_id,
           media_attribute, media_province, media_city, media_channel_form, media_category,
           media_fit_score, response_note, status)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
          """, no("INV"), taskId,
          item.get("candidateType") == null ? "MANUAL" : item.get("candidateType"),
          item.get("journalistName"),
          item.get("mediaName"), item.get("externalMediaId"), item.get("externalReporterId"),
          item.get("mediaAttribute"), item.get("mediaProvince"),
          item.get("mediaCity"), item.get("mediaChannelForm"), item.get("mediaCategory"),
          item.get("mediaScore"), item.get("note"));
    } else {
      jdbc.update("""
          INSERT INTO direct_publish_order
          (order_no, publish_task_id, channel_quote_id, article_title, amount,
           price_valid_until, requirement_note, status)
          VALUES (?, ?, ?, ?, ?, ?, ?, 'SUBMITTED')
          """, no("ORD"), taskId, item.get("quoteId"), item.get("manuscriptTitle"),
          item.get("unitPriceSnapshot"), item.get("priceValidUntil"), item.get("note"));
    }
    if (channelId != null) {
      String supplierOrderNo = no("SUP-ORD");
      KeyHolder supplierOrderKey = new GeneratedKeyHolder();
      jdbc.update(connection -> {
        PreparedStatement ps = connection.prepareStatement("""
            INSERT INTO supplier_order
            (supplier_order_no, supplier_id, publish_plan_id, publish_task_id, channel_id,
             channel_quote_id, customer_price_snapshot, cost_price_snapshot, currency,
             article_title, planned_publish_at, submission_note, assigned_operator_id, status)
            SELECT ?, ?, ?, ?, ?, ?, ?, ?, 'CNY', ?, ?, ?, t.assigned_operator_id, 'PENDING_SUBMISSION'
            FROM publish_task t WHERE t.id=?
            """, Statement.RETURN_GENERATED_KEYS);
        ps.setString(1, supplierOrderNo);
        ps.setObject(2, item.get("supplierId"), java.sql.Types.BIGINT);
        ps.setLong(3, number(item.get("publishPlanId")));
        ps.setLong(4, taskId);
        ps.setLong(5, channelId);
        ps.setObject(6, item.get("quoteId"), java.sql.Types.BIGINT);
        ps.setObject(7, item.get("unitPriceSnapshot"), java.sql.Types.NUMERIC);
        ps.setObject(8, item.get("costPriceSnapshot"), java.sql.Types.NUMERIC);
        ps.setString(9, item.get("manuscriptTitle") == null
            ? null : String.valueOf(item.get("manuscriptTitle")));
        ps.setObject(10, item.get("plannedPublishAt"));
        ps.setString(11, item.get("note") == null ? null : String.valueOf(item.get("note")));
        ps.setLong(12, taskId);
        return ps;
      }, supplierOrderKey);
      Long supplierOrderId = key(supplierOrderKey);
      jdbc.update("""
          INSERT INTO supplier_order_status_history
          (history_no, supplier_order_id, previous_status, current_status, note, changed_by)
          VALUES (?, ?, NULL, 'PENDING_SUBMISSION', '客户已确认发布计划，等待平台提交供应商', ?)
          """, no("SUP-HIS"), supplierOrderId, user.userId());
    }
    jdbc.update("UPDATE publish_plan_item SET status='TASK_CREATED' WHERE id=?", item.get("planItemId"));
    Map<String, Object> taskAudit = new LinkedHashMap<>();
    taskAudit.put("channelType", type);
    taskAudit.put("planItemId", item.get("planItemId"));
    taskAudit.put("executionChannelBound", channelId != null);
    log(user, "CREATE_PUBLISH_TASK", "PUBLISH_TASK", taskNo,
        taskAudit);
    return taskId;
  }

  public void markPublishPlanConfirmed(AuthPrincipal user, Long planId, Long projectId, Long manuscriptId) {
    jdbc.update("""
        UPDATE publish_plan SET status='CONFIRMED', confirmed_by=?, confirmed_at=CURRENT_TIMESTAMP
        WHERE id=? AND status='WAITING_CONFIRMATION'
        """, user.userId(), planId);
    jdbc.update("""
        UPDATE service_intake_task
        SET status='COMPLETED', completed_at=COALESCE(completed_at, CURRENT_TIMESTAMP),
            customer_visible_note='服务范围已确认，执行任务已建立',
            updated_at=CURRENT_TIMESTAMP
        WHERE project_id=? AND status NOT IN ('COMPLETED','CANCELLED')
        """, projectId);
    // Do not advance the customer project or manuscript to "publishing" at this point.  The
    // customer has confirmed a plan, but channel availability, price, timing and any external
    // action are still subject to project verification.
    log(user, "CONFIRM_PUBLISH_PLAN", "PUBLISH_PLAN", String.valueOf(planId), Map.of("projectId", projectId));
  }

  public Long createPublishTask(AuthPrincipal user, Long projectId, Long manuscriptId, Long versionId,
                                Map<String, Object> channel, ChannelSelection selection) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    String taskNo = no("PUB");
    jdbc.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO publish_task
          (task_no, project_id, manuscript_id, manuscript_version_id, channel_id, channel_type,
           planned_publish_at, execution_note, status)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_ASSIGNMENT')
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, taskNo);
      ps.setLong(2, projectId);
      ps.setLong(3, manuscriptId);
      ps.setLong(4, versionId);
      ps.setLong(5, ((Number) channel.get("id")).longValue());
      ps.setString(6, String.valueOf(channel.get("channel_type")));
      ps.setObject(7, selection.plannedPublishAt());
      ps.setString(8, selection.note());
      return ps;
    }, keyHolder);
    Long taskId = key(keyHolder);
    jdbc.update("""
        UPDATE publish_task t SET assigned_operator_id=p.owner_operator_id,
          status=CASE WHEN p.owner_operator_id IS NULL THEN 'PENDING_ASSIGNMENT' ELSE 'PENDING_EXECUTION' END
        FROM project p WHERE t.id=? AND p.id=t.project_id
        """, taskId);
    String type = String.valueOf(channel.get("channel_type"));
    if ("MEDIA_PR".equals(type)) {
      MediaCandidate candidate = selection.mediaCandidate();
      jdbc.update("""
          INSERT INTO media_pr_invitation
          (invitation_no, publish_task_id, candidate_type, journalist_name, media_name,
           external_media_id, external_reporter_id,
           media_attribute, media_province, media_city, media_channel_form, media_category,
           media_fit_score, response_note, status)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
          """, no("INV"), taskId, candidate == null ? "MANUAL" : candidate.candidateType(),
          selection.journalistName(), selection.mediaName(),
          candidate == null ? null : candidate.mediaId(),
          candidate == null ? null : candidate.reporterId(),
          candidate == null ? null : candidate.attribute(),
          candidate == null ? null : candidate.province(),
          candidate == null ? null : candidate.city(),
          candidate == null ? null : candidate.channelForm(),
          candidate == null ? null : candidate.category(),
          candidate == null ? null : candidate.score(), selection.note());
    } else if ("DIRECT_PUBLISHING".equals(type)) {
      Object quoteId = channel.get("quote_id");
      Object price = channel.get("customer_price");
      Object validUntil = channel.get("valid_until");
      jdbc.update("""
          INSERT INTO direct_publish_order
          (order_no, publish_task_id, channel_quote_id, article_title, amount, price_valid_until, requirement_note, status)
          VALUES (?, ?, ?, ?, ?, ?, ?, 'SUBMITTED')
          """, no("ORD"), taskId, quoteId, channel.get("title"), price, validUntil, selection.note());
    }
    log(user, "CREATE_PUBLISH_TASK", "PUBLISH_TASK", taskNo, Map.of("channelType", type, "channelId", channel.get("id")));
    return taskId;
  }

  public Map<String, Object> mediaInvitationForTask(Long taskId) {
    return one("""
        SELECT status, invited_at AS "invitedAt"
        FROM media_pr_invitation
        WHERE publish_task_id=?
        """, taskId);
  }

  public boolean updateMediaInvitation(
      AuthPrincipal user, Long taskId, String status, String note) {
    int updated = jdbc.update("""
        UPDATE media_pr_invitation
        SET status=?,
            invited_at=CASE WHEN ?='INVITED' THEN COALESCE(invited_at, CURRENT_TIMESTAMP) ELSE invited_at END,
            response_at=CASE WHEN ? IN ('RESPONDED','DECLINED','ATTENDING')
              THEN COALESCE(response_at, CURRENT_TIMESTAMP) ELSE response_at END,
            response_note=COALESCE(NULLIF(?, ''), response_note),
            updated_at=CURRENT_TIMESTAMP
        WHERE publish_task_id=?
        """, status, status, status, note, taskId);
    if (updated > 0) {
      String taskStatus = switch (status) {
        case "DECLINED", "NOT_PROCEEDING" -> "NOT_PROCEEDING";
        default -> "IN_PROGRESS";
      };
      jdbc.update("""
          UPDATE publish_task
          SET status=?,
              execution_note=COALESCE(NULLIF(?, ''), execution_note),
              exception_reason=NULL,
              updated_at=CURRENT_TIMESTAMP
          WHERE id=?
          """, taskStatus, note, taskId);
      syncPublishExecutionProgress(taskId);
      log(user, "UPDATE_MEDIA_INVITATION", "PUBLISH_TASK", String.valueOf(taskId), Map.of("status", status));
    }
    return updated > 0;
  }

  public List<Map<String, Object>> tasks(AuthPrincipal user, String status, String scope, String type, int limit, int offset) {
    StringBuilder where = new StringBuilder(" WHERE 1=1");
    List<Object> args = taskScope(where, user);
    if (status != null && !status.isBlank()) { where.append(" AND t.status=?"); args.add(status); }
    applyTaskScope(where, scope);
    if (type != null && !type.isBlank()) { where.append(" AND t.channel_type=?"); args.add(type); }
    where.append(" ORDER BY t.updated_at DESC LIMIT ? OFFSET ?"); args.add(limit); args.add(offset);
    return taskRows(where.toString(), args.toArray());
  }

  public long tasksCount(AuthPrincipal user, String status, String scope, String type) {
    StringBuilder sql = new StringBuilder("SELECT count(*) FROM publish_task t JOIN project p ON p.id=t.project_id WHERE 1=1");
    List<Object> args = taskScope(sql, user);
    if (status != null && !status.isBlank()) { sql.append(" AND t.status=?"); args.add(status); }
    applyTaskScope(sql, scope);
    if (type != null && !type.isBlank()) { sql.append(" AND t.channel_type=?"); args.add(type); }
    return count(sql.toString(), args.toArray());
  }

  private void applyTaskScope(StringBuilder sql, String scope) {
    if ("pending".equals(scope)) {
      sql.append(" AND t.status NOT IN ('COMPLETED','CLIENT_ACCEPTED','NOT_PROCEEDING')");
    } else if ("withResults".equals(scope)) {
      sql.append(" AND EXISTS (SELECT 1 FROM result_link r WHERE r.publish_task_id=t.id AND r.status='VERIFIED')");
    } else if ("awaitingAcceptance".equals(scope)) {
      sql.append(" AND t.status='COMPLETED'");
    }
  }

  public boolean canOperateTask(AuthPrincipal user, Long taskId) {
    if ("PLATFORM_ADMIN".equals(user.role())) return count("SELECT count(*) FROM publish_task WHERE id=?", taskId) > 0;
    return count("SELECT count(*) FROM publish_task WHERE id=? AND assigned_operator_id=?", taskId, user.userId()) > 0;
  }

  public Map<String, Object> lockConferenceWorkItemForUpdate(
      AuthPrincipal user, Long projectId, Long itemId) {
    if ("PLATFORM_ADMIN".equals(user.role())) {
      return one("""
          SELECT cwi.id, cwi.status, cwi.completed_at AS "completedAt"
          FROM conference_work_item cwi
          JOIN conference_project cp ON cp.id=cwi.conference_project_id
          WHERE cwi.id=? AND cp.project_id=?
          FOR UPDATE OF cwi
          """, itemId, projectId);
    }
    return one("""
        SELECT cwi.id, cwi.status, cwi.completed_at AS "completedAt"
        FROM conference_work_item cwi
        JOIN conference_project cp ON cp.id=cwi.conference_project_id
        JOIN project p ON p.id=cp.project_id
        WHERE cwi.id=? AND cp.project_id=? AND (cwi.assigned_operator_id=? OR p.owner_operator_id=?)
        FOR UPDATE OF cwi
        """, itemId, projectId, user.userId(), user.userId());
  }

  public boolean hasConferenceProject(Long projectId) {
    return count("SELECT count(*) FROM conference_project WHERE project_id=?", projectId) > 0;
  }

  public boolean operatorExists(Long operatorId) {
    return count("""
        SELECT count(*) FROM app_user
        WHERE id=? AND role IN ('PUBLISH_OPERATOR','PLATFORM_ADMIN') AND status='ACTIVE'
        """, operatorId) > 0;
  }

  public boolean updateConferenceProject(
      AuthPrincipal user, Long projectId, UpdateConferenceProjectRequest request) {
    int updated = jdbc.update("""
        UPDATE conference_project
        SET theme=?, event_time=?, event_location=?, conference_type=?, conference_format=?,
            attendee_scale=?, media_goal=?, guest_plan=?, agenda_plan=?, venue_plan=?,
            media_direction=?, communication_goal=?,
            agenda_status=COALESCE(NULLIF(?, ''), agenda_status),
            venue_status=COALESCE(NULLIF(?, ''), venue_status),
            contact_name=?, contact_mobile=?,
            status=CASE WHEN status='PENDING_SCOPE' THEN 'PLANNING' ELSE status END
        WHERE project_id=?
        """, blankToNull(request.theme()), request.eventTime(), blankToNull(request.eventLocation()),
        blankToNull(request.conferenceType()), blankToNull(request.conferenceFormat()),
        blankToNull(request.conferenceScale()), blankToNull(request.mediaGoal()),
        blankToNull(request.guestPlan()), blankToNull(request.agendaPlan()),
        blankToNull(request.venuePlan()), blankToNull(request.mediaDirection()),
        blankToNull(request.communicationGoal()), request.agendaStatus(), request.venueStatus(),
        request.contactName().trim(), request.contactMobile().trim(), projectId);
    if (updated > 0) {
      jdbc.update("""
          UPDATE customer_requirement r
          SET event_time=?, event_location=?, objective=COALESCE(?, r.objective)
          FROM project p
          WHERE p.id=? AND p.requirement_id=r.id
          """, request.eventTime(), blankToNull(request.eventLocation()),
          blankToNull(request.communicationGoal()), projectId);
      jdbc.update("""
          UPDATE project
          SET project_name=COALESCE(?, project_name),
              planned_start_at=COALESCE(?, planned_start_at)
          WHERE id=?
          """, blankToNull(request.theme()), request.eventTime(), projectId);
      log(user, "UPDATE_NEWS_CONFERENCE", "CONFERENCE_PROJECT", String.valueOf(projectId),
          Map.of("projectId", projectId));
    }
    return updated > 0;
  }

  public boolean addConferenceMediaCandidate(AuthPrincipal user, Long projectId, MediaCandidate candidate) {
    int inserted = jdbc.update("""
        INSERT INTO conference_media_candidate
        (candidate_no, conference_project_id, candidate_key, candidate_type,
         external_media_id, media_name, external_reporter_id, reporter_name,
         media_attribute, province, city, channel_form, category, coverage_tags,
         operation_note, fit_score, reporter_news_count, media_fans_count, logo_url,
         reporter_avatar_url,
         selected_by, selected_at, status)
        SELECT ?, cp.id, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
               CURRENT_TIMESTAMP, 'CANDIDATE'
        FROM conference_project cp WHERE cp.project_id=?
        ON CONFLICT (conference_project_id, candidate_key) DO NOTHING
        """, no("CMC"), candidate.candidateKey(), candidate.candidateType(),
        candidate.mediaId(), candidate.displayName(), candidate.reporterId(), candidate.reporterName(),
        candidate.attribute(), candidate.province(), candidate.city(), candidate.channelForm(), candidate.category(),
        candidate.coverageTags() == null ? "" : String.join("、", candidate.coverageTags()),
        candidate.operationNote(), candidate.score(), candidate.newsCount(), candidate.fansCount(),
        candidate.logoUrl(), candidate.avatarUrl(),
        user.userId(), projectId);
    if (inserted > 0) {
      log(user, "ADD_CONFERENCE_MEDIA_CANDIDATE", "CONFERENCE_MEDIA_CANDIDATE", candidate.candidateKey(),
          Map.of("projectId", projectId, "mediaName", candidate.displayName()));
    }
    return inserted > 0;
  }

  public Map<String, Object> lockConferenceMediaCandidateForUpdate(
      AuthPrincipal user, Long projectId, Long candidateId) {
    if ("PLATFORM_ADMIN".equals(user.role())) {
      return one("""
          SELECT cmc.id, cmc.status, cmc.invited_at AS "invitedAt", cmc.responded_at AS "respondedAt"
          FROM conference_media_candidate cmc
          JOIN conference_project cp ON cp.id=cmc.conference_project_id
          WHERE cmc.id=? AND cp.project_id=?
          FOR UPDATE OF cmc
          """, candidateId, projectId);
    }
    return one("""
        SELECT cmc.id, cmc.status, cmc.invited_at AS "invitedAt", cmc.responded_at AS "respondedAt"
        FROM conference_media_candidate cmc
        JOIN conference_project cp ON cp.id=cmc.conference_project_id
        JOIN project p ON p.id=cp.project_id
        WHERE cmc.id=? AND cp.project_id=?
          AND (cmc.managed_operator_id=? OR p.owner_operator_id=?)
        FOR UPDATE OF cmc
        """, candidateId, projectId, user.userId(), user.userId());
  }

  public boolean updateConferenceMediaCandidate(
      AuthPrincipal user, Long projectId, Long candidateId, String expectedStatus, String status, String note) {
    int updated = jdbc.update("""
        UPDATE conference_media_candidate cmc
        SET status=?, note=COALESCE(?, cmc.note), managed_operator_id=COALESCE(cmc.managed_operator_id, ?),
            invited_at=CASE WHEN ?='INVITED' THEN COALESCE(cmc.invited_at, CURRENT_TIMESTAMP) ELSE cmc.invited_at END,
            responded_at=CASE WHEN ? IN ('RESPONDED','DECLINED','ATTENDING')
              THEN COALESCE(cmc.responded_at, CURRENT_TIMESTAMP) ELSE cmc.responded_at END
        FROM conference_project cp
        WHERE cmc.id=? AND cmc.conference_project_id=cp.id AND cp.project_id=? AND cmc.status=?
        """, status, blankToNull(note), user.userId(), status, status, candidateId, projectId, expectedStatus);
    if (updated > 0) {
      log(user, "UPDATE_CONFERENCE_MEDIA_CANDIDATE", "CONFERENCE_MEDIA_CANDIDATE", String.valueOf(candidateId),
          Map.of("projectId", projectId, "status", status));
    }
    return updated > 0;
  }

  public boolean updateConferenceWorkItem(
      AuthPrincipal user, Long projectId, Long itemId, String expectedStatus, String status, String note,
      OffsetDateTime dueAt, Long assignedOperatorId) {
    int updated = jdbc.update("""
        UPDATE conference_work_item cwi
        SET status=?, note=COALESCE(?, cwi.note), due_at=COALESCE(?, cwi.due_at),
            assigned_operator_id=COALESCE(?, cwi.assigned_operator_id, ?),
            completed_at=CASE
              WHEN ?='COMPLETED' THEN COALESCE(cwi.completed_at, CURRENT_TIMESTAMP)
              ELSE NULL
            END
        FROM conference_project cp
        WHERE cwi.id=? AND cwi.conference_project_id=cp.id AND cp.project_id=? AND cwi.status=?
        """, status, blankToNull(note), dueAt, assignedOperatorId, user.userId(),
        status, itemId, projectId, expectedStatus);
    if (updated > 0) {
      syncConferenceProgress(projectId);
      log(user, "UPDATE_CONFERENCE_WORK_ITEM", "CONFERENCE_WORK_ITEM", String.valueOf(itemId),
          Map.of("projectId", projectId, "status", status));
    }
    return updated > 0;
  }

  /**
   * Keeps the customer order, project list and nine-item conference workspace on one state
   * timeline. The checklist remains the execution source of truth; no separate manual project
   * completion flag can drift away from it.
   */
  private void syncConferenceProgress(Long projectId) {
    jdbc.update("""
        UPDATE conference_project cp
        SET status=CASE
          WHEN cp.status='CANCELLED' THEN 'CANCELLED'
          WHEN EXISTS (
            SELECT 1 FROM conference_work_item item
            WHERE item.conference_project_id=cp.id
          ) AND NOT EXISTS (
            SELECT 1 FROM conference_work_item item
            WHERE item.conference_project_id=cp.id AND item.status<>'COMPLETED'
          ) THEN 'COMPLETED'
          WHEN EXISTS (
            SELECT 1 FROM conference_work_item item
            WHERE item.conference_project_id=cp.id AND item.status<>'PENDING'
          ) THEN 'EXECUTING'
          WHEN cp.status IN ('EXECUTING','COMPLETED') THEN 'PLANNING'
          ELSE cp.status
        END
        WHERE cp.project_id=?
        """, projectId);
    jdbc.update("""
        UPDATE project p
        SET status=CASE cp.status
          WHEN 'EXECUTING' THEN 'IN_PROGRESS'
          WHEN 'COMPLETED' THEN 'COMPLETED'
          WHEN 'CANCELLED' THEN 'CANCELLED'
          ELSE 'PLANNING'
        END
        FROM conference_project cp
        WHERE cp.project_id=p.id AND p.id=?
        """, projectId);
  }

  public Map<String, Object> task(Long taskId) {
    return oneTask(" WHERE t.id=?", taskId);
  }

  public Map<String, Object> taskByNo(String taskNo) {
    return oneTask(" WHERE t.task_no=?", taskNo);
  }

  public Map<String, Object> lockTaskForUpdate(Long taskId) {
    return oneTask(" WHERE t.id=? FOR UPDATE OF t", taskId);
  }

  public Map<String, Object> lockTaskByNoForUpdate(String taskNo) {
    return oneTask(" WHERE t.task_no=? FOR UPDATE OF t", taskNo);
  }

  public boolean updateTask(
      AuthPrincipal user, Long taskId, String expectedStatus, String status,
      String note, String exceptionReason) {
    int updated = jdbc.update("""
        UPDATE publish_task
        SET assigned_operator_id=COALESCE(assigned_operator_id, ?),
            status=?,
            execution_note=COALESCE(NULLIF(?, ''), execution_note),
            exception_reason=CASE WHEN ?='EXCEPTION' THEN ? ELSE NULL END,
            updated_at=CURRENT_TIMESTAMP
        WHERE id=? AND status=?
        """, user.userId(), status, note, status, exceptionReason, taskId, expectedStatus);
    if (updated == 0) return false;
    if ("EXCEPTION".equals(status)) {
      Map<String, Object> task = task(taskId);
      if ("MEDIA_PR".equals(task.get("channelType"))) {
        // An execution exception is a platform-side task fact, not evidence that a media
        // contact declined. Keep the invitation's contact state unchanged unless an operator
        // records an actual invitation or response through the dedicated workflow.
        releaseMediaPrLock(task);
      }
    }
    syncPublishExecutionProgress(taskId);
    log(user, "UPDATE_PUBLISH_TASK", "PUBLISH_TASK", String.valueOf(taskId), Map.of("status", status));
    return true;
  }

  public boolean acceptTask(AuthPrincipal user, Long taskId) {
    int updated = jdbc.update("""
        UPDATE publish_task
        SET status='CLIENT_ACCEPTED', client_accepted_at=CURRENT_TIMESTAMP,
            updated_at=CURRENT_TIMESTAMP
        WHERE id=? AND status='COMPLETED'
        """, taskId);
    if (updated == 0) return false;
    syncPublishExecutionProgress(taskId);
    log(user, "ACCEPT_PUBLISH_RESULT", "PUBLISH_TASK", String.valueOf(taskId), Map.of());
    return true;
  }

  public boolean hasVerifiedResultForTask(Long taskId) {
    return count("""
        SELECT count(*) FROM result_link
        WHERE publish_task_id=? AND status='VERIFIED'
        """, taskId) > 0;
  }

  @Transactional
  public boolean submitResult(
      AuthPrincipal user, Long taskId, String expectedStatus, String title, String url,
      OffsetDateTime publishedAt, String note) {
    Map<String, Object> task = task(taskId);
    jdbc.update("""
        INSERT INTO result_link
        (result_no, project_id, publish_task_id, channel_name, title, url, published_at, verified_by, verified_at, status)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 'VERIFIED')
        """, no("RES"), task.get("projectId"), taskId, task.get("channelName"), title, url,
        publishedAt == null ? OffsetDateTime.now() : publishedAt, user.userId());
    if ("MEDIA_PR".equals(task.get("channelType"))) {
      int invitationUpdated = jdbc.update("""
          UPDATE media_pr_invitation
          SET status='REPORTED', updated_at=CURRENT_TIMESTAMP
          WHERE publish_task_id=?
            AND status IN ('INVITED','RESPONDED','ATTENDING')
          """, taskId);
      if (invitationUpdated == 0) return false;
    }
    int updated = jdbc.update("""
        UPDATE publish_task
        SET status='COMPLETED',
            actual_publish_at=COALESCE(?, CURRENT_TIMESTAMP),
            updated_at=CURRENT_TIMESTAMP
        WHERE id=? AND status=?
        """, publishedAt, taskId, expectedStatus);
    if (updated == 0) return false;
    jdbc.update("""
        INSERT INTO monitoring_record
        (monitoring_no, project_id, publish_task_id, monitored_at, metric_name, metric_value, metric_text, source_url, status)
        VALUES (?, ?, ?, CURRENT_TIMESTAMP, 'LINK_AVAILABLE', 1, ?, ?, 'VERIFIED')
        """, no("MON"), task.get("projectId"), taskId, note == null || note.isBlank() ? "成果链接已核验" : note, url);
    if ("MEDIA_PR".equals(task.get("channelType"))) {
      releaseMediaPrLock(task);
    }
    syncPublishExecutionProgress(taskId);
    log(user, "SUBMIT_PUBLISH_RESULT", "PUBLISH_TASK", String.valueOf(taskId), Map.of("url", url));
    return true;
  }

  /**
   * Keeps a publish plan, its owning project and the customer order projection on the same
   * evidence-backed timeline. A confirmed plan is not treated as execution until a task records
   * actual work. A media target that declines is terminal for that target without being presented
   * as a verified publication result.
   */
  private void syncPublishExecutionProgress(Long taskId) {
    jdbc.update("""
        UPDATE publish_plan pp
        SET status=CASE
              WHEN EXISTS (
                SELECT 1
                FROM publish_plan_item item
                JOIN publish_task task ON task.publish_plan_item_id=item.id
                WHERE item.publish_plan_id=pp.id
              ) AND NOT EXISTS (
                SELECT 1
                FROM publish_plan_item item
                JOIN publish_task task ON task.publish_plan_item_id=item.id
                WHERE item.publish_plan_id=pp.id
                  AND task.status NOT IN ('COMPLETED','CLIENT_ACCEPTED','NOT_PROCEEDING')
              ) THEN 'COMPLETED'
              WHEN EXISTS (
                SELECT 1
                FROM publish_plan_item item
                JOIN publish_task task ON task.publish_plan_item_id=item.id
                WHERE item.publish_plan_id=pp.id
                  AND task.status IN (
                    'IN_PROGRESS','NEEDS_INFO','EXCEPTION',
                    'COMPLETED','CLIENT_ACCEPTED','NOT_PROCEEDING'
                  )
              ) THEN 'EXECUTING'
              ELSE 'CONFIRMED'
            END,
            updated_at=CURRENT_TIMESTAMP
        WHERE pp.id=(
          SELECT item.publish_plan_id
          FROM publish_task task
          JOIN publish_plan_item item ON item.id=task.publish_plan_item_id
          WHERE task.id=?
        )
          AND pp.status NOT IN ('DRAFT','WAITING_CONFIRMATION','CANCELLED')
        """, taskId);
    jdbc.update("""
        UPDATE project p
        SET status=CASE
              WHEN p.status='CANCELLED' THEN 'CANCELLED'
              WHEN EXISTS (
                SELECT 1 FROM publish_plan waiting
                WHERE waiting.project_id=p.id AND waiting.status='WAITING_CONFIRMATION'
              ) THEN CASE
                WHEN EXISTS (
                  SELECT 1 FROM publish_task task
                  WHERE task.project_id=p.id
                    AND task.status IN ('IN_PROGRESS','NEEDS_INFO','EXCEPTION')
                ) THEN 'IN_PROGRESS'
                ELSE 'PLANNING'
              END
              WHEN EXISTS (
                SELECT 1 FROM publish_task task WHERE task.project_id=p.id
              ) AND NOT EXISTS (
                SELECT 1 FROM publish_task task
                WHERE task.project_id=p.id
                  AND task.status NOT IN ('COMPLETED','CLIENT_ACCEPTED','NOT_PROCEEDING')
              ) THEN 'COMPLETED'
              WHEN EXISTS (
                SELECT 1 FROM publish_task task
                WHERE task.project_id=p.id
                  AND task.status IN ('IN_PROGRESS','NEEDS_INFO','EXCEPTION')
              ) THEN 'IN_PROGRESS'
              ELSE 'PLANNING'
            END,
            updated_at=CURRENT_TIMESTAMP
        FROM customer_requirement requirement
        WHERE requirement.id=p.requirement_id
          AND requirement.requested_service IN ('MEDIA_PR','DIRECT_PUBLISHING')
          AND p.id=(SELECT project_id FROM publish_task WHERE id=?)
        """, taskId);
    jdbc.update("""
        UPDATE direct_publish_order direct_order
        SET status=CASE task.status
              WHEN 'COMPLETED' THEN 'COMPLETED'
              WHEN 'CLIENT_ACCEPTED' THEN 'COMPLETED'
              WHEN 'EXCEPTION' THEN 'EXCEPTION'
              WHEN 'IN_PROGRESS' THEN 'IN_PROGRESS'
              WHEN 'NEEDS_INFO' THEN 'IN_PROGRESS'
              ELSE 'SUBMITTED'
            END,
            updated_at=CURRENT_TIMESTAMP
        FROM publish_task task
        WHERE direct_order.publish_task_id=task.id AND task.id=?
        """, taskId);
  }

  private void releaseMediaPrLock(Map<String, Object> task) {
    Object manuscriptId = task.get("manuscriptId");
    if (manuscriptId instanceof Number number) releaseLock(number.longValue());
  }

  public List<Map<String, Object>> operators() {
    return jdbc.queryForList("""
        SELECT u.id, u.user_no AS "userNo", u.display_name AS "displayName", u.email
        FROM app_user u JOIN user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id
        WHERE r.role_code='PUBLISH_OPERATOR' AND u.status='ACTIVE' ORDER BY u.display_name
        """);
  }

  public boolean assignProject(AuthPrincipal user, Long projectId, Long operatorId) {
    int updated = jdbc.update("UPDATE project SET owner_operator_id=?, status=CASE WHEN status='PLANNING' THEN 'IN_PROGRESS' ELSE status END WHERE id=?", operatorId, projectId);
    if (updated == 0) return false;
    jdbc.update("UPDATE editorial_task SET assigned_operator_id=?, status=CASE WHEN status='PENDING_ASSIGNMENT' THEN 'PENDING_EXECUTION' ELSE status END WHERE project_id=?", operatorId, projectId);
    jdbc.update("""
        UPDATE service_intake_task SET assigned_operator_id=?,
          status=CASE WHEN status='PENDING_ACCEPTANCE' THEN 'IN_PROGRESS' ELSE status END
        WHERE project_id=?
        """, operatorId, projectId);
    jdbc.update("UPDATE publish_task SET assigned_operator_id=?, status=CASE WHEN status='PENDING_ASSIGNMENT' THEN 'PENDING_EXECUTION' ELSE status END WHERE project_id=? AND assigned_operator_id IS NULL", operatorId, projectId);
    jdbc.update("""
        UPDATE conference_work_item cwi SET assigned_operator_id=?
        FROM conference_project cp
        WHERE cwi.conference_project_id=cp.id AND cp.project_id=? AND cwi.assigned_operator_id IS NULL
        """, operatorId, projectId);
    log(user, "ASSIGN_PROJECT", "PROJECT", String.valueOf(projectId), Map.of("operatorId", operatorId));
    return true;
  }

  public List<Map<String, Object>> writerProfiles() {
    return jdbc.queryForList("""
        SELECT wp.id, wp.writer_no AS "writerNo", wp.province, wp.city,
               wp.service_radius_km AS "serviceRadiusKm", wp.expertise_tags AS "expertiseTags",
               wp.availability_status AS "availabilityStatus", wp.status,
               u.display_name AS "displayName"
        FROM writer_profile wp JOIN app_user u ON u.id=wp.user_id
        WHERE wp.status='ACTIVE' ORDER BY wp.availability_status, wp.city, u.display_name
        """);
  }

  public List<Map<String, Object>> writingAssignments(AuthPrincipal user, String status) {
    boolean writerView = "PUBLISH_OPERATOR".equals(user.role());
    String viewerFields = writerView
        ? """
            viewer_member.status AS "memberStatus",
            viewer_member.distance_km AS "memberDistanceKm",
            viewer_member.offered_at AS "offeredAt",
            viewer_member.responded_at AS "respondedAt"
            """
        : """
            NULL::VARCHAR AS "memberStatus",
            NULL::NUMERIC AS "memberDistanceKm",
            wa.offered_at AS "offeredAt",
            wa.responded_at AS "respondedAt"
            """;
    String viewerJoin = writerView
        ? """
            JOIN writing_assignment_member viewer_member ON viewer_member.assignment_id=wa.id
            JOIN writer_profile viewer_profile
              ON viewer_profile.id=viewer_member.writer_profile_id AND viewer_profile.user_id=?
            """
        : "";
    StringBuilder sql = new StringBuilder("""
        SELECT wa.id, wa.assignment_no AS "assignmentNo", p.id AS "projectId",
               p.project_no AS "projectNo", p.project_name AS "projectName", r.event_time AS "eventTime",
               wa.service_location AS "serviceLocation", wa.matching_mode AS "matchingMode",
               wa.service_days AS "serviceDays", wa.writer_count AS "writerCount",
               wa.unit_price_snapshot AS "unitPrice", wa.estimated_amount_snapshot AS "estimatedAmount",
               wa.status,
               slots.accepted_count AS "acceptedWriterCount",
               slots.offered_count AS "offeredWriterCount",
               GREATEST(wa.writer_count-slots.accepted_count-slots.offered_count, 0) AS "openWriterSlots",
               roster.writer_names AS "writerNames",
        %s
        FROM writing_assignment wa
        JOIN editorial_task et ON et.id=wa.editorial_task_id
        JOIN project p ON p.id=et.project_id
        JOIN customer_requirement r ON r.id=et.requirement_id
        %s
        LEFT JOIN LATERAL (
          SELECT
            count(*) FILTER (WHERE member.status='ACCEPTED')::INT AS accepted_count,
            count(*) FILTER (WHERE member.status='OFFERED')::INT AS offered_count
          FROM writing_assignment_member member
          WHERE member.assignment_id=wa.id
        ) slots ON TRUE
        LEFT JOIN LATERAL (
          SELECT string_agg(
            writer_user.display_name || ' · ' || CASE member.status
              WHEN 'OFFERED' THEN '待接单'
              WHEN 'ACCEPTED' THEN '已接单'
              WHEN 'COMPLETED' THEN '已完成'
              ELSE member.status
            END,
            '；' ORDER BY writer_user.display_name
          ) AS writer_names
          FROM writing_assignment_member member
          JOIN writer_profile profile ON profile.id=member.writer_profile_id
          JOIN app_user writer_user ON writer_user.id=profile.user_id
          WHERE member.assignment_id=wa.id
            AND member.status IN ('OFFERED','ACCEPTED','COMPLETED')
        ) roster ON TRUE
        WHERE 1=1
        """.formatted(viewerFields, viewerJoin));
    List<Object> args = new ArrayList<>();
    if (writerView) {
      args.add(user.userId());
    }
    if (status != null && !status.isBlank()) {
      sql.append(" AND wa.status=?");
      args.add(status);
    }
    sql.append(" ORDER BY wa.updated_at DESC, wa.id DESC");
    return jdbc.queryForList(sql.toString(), args.toArray());
  }

  @Transactional
  public WritingAssignmentOfferOutcome offerWritingAssignment(
      AuthPrincipal user, Long assignmentId, Long writerProfileId, BigDecimal distanceKm) {
    Map<String, Object> assignment = writingAssignmentForUpdate(assignmentId);
    if (assignment.isEmpty() || assignment.get("eventTime") == null) {
      return WritingAssignmentOfferOutcome.NOT_OFFERABLE;
    }
    String assignmentStatus = String.valueOf(assignment.get("status"));
    if (List.of("ACCEPTED", "COMPLETED", "CANCELLED").contains(assignmentStatus)) {
      return WritingAssignmentOfferOutcome.NOT_OFFERABLE;
    }
    int writerCount = ((Number) assignment.get("writerCount")).intValue();
    if (activeWritingAssignmentSeatCount(assignmentId) >= writerCount) {
      return WritingAssignmentOfferOutcome.NO_OPEN_SLOT;
    }
    WritingAssignmentOfferOutcome writerEligibility =
        writingAssignmentWriterEligibility(writerProfileId, distanceKm);
    if (writerEligibility != null) return writerEligibility;
    if (writingAssignmentHasWriter(assignmentId, writerProfileId)) {
      return WritingAssignmentOfferOutcome.DUPLICATE_WRITER;
    }
    if (writerHasConfirmedScheduleConflict(assignmentId, writerProfileId)) {
      return WritingAssignmentOfferOutcome.SCHEDULE_CONFLICT;
    }

    int inserted = jdbc.update("""
        INSERT INTO writing_assignment_member
        (member_no, assignment_id, writer_profile_id, service_window, distance_km, status, offered_at)
        SELECT ?, wa.id, ?,
               tstzrange(r.event_time, r.event_time + (wa.service_days * INTERVAL '1 day'), '[)'),
               ?, 'OFFERED', CURRENT_TIMESTAMP
        FROM writing_assignment wa
        JOIN editorial_task et ON et.id=wa.editorial_task_id
        JOIN customer_requirement r ON r.id=et.requirement_id
        WHERE wa.id=? AND r.event_time IS NOT NULL
        """, no("WRT-MBR"), writerProfileId, distanceKm, assignmentId);
    if (inserted == 0) {
      return WritingAssignmentOfferOutcome.NOT_OFFERABLE;
    }
    jdbc.update("UPDATE writing_assignment SET offered_at=CURRENT_TIMESTAMP WHERE id=?", assignmentId);
    String refreshedStatus = refreshWritingAssignmentStatus(assignmentId, writerCount);
    log(user, "OFFER_WRITING_ASSIGNMENT", "WRITING_ASSIGNMENT", String.valueOf(assignmentId),
        Map.of("writerProfileId", writerProfileId, "assignmentStatus", refreshedStatus));
    return WritingAssignmentOfferOutcome.OFFERED;
  }

  @Transactional
  public boolean respondWritingAssignment(
      AuthPrincipal user, Long assignmentId, String decision, String note) {
    Map<String, Object> assignment = writingAssignmentForUpdate(assignmentId);
    if (assignment.isEmpty()) return false;
    String memberStatus = "ACCEPT".equals(decision) ? "ACCEPTED" : "DECLINED";
    int updated = jdbc.update("""
        UPDATE writing_assignment_member member
        SET status=?, responded_at=CURRENT_TIMESTAMP, response_note=?
        FROM writer_profile wp
        WHERE member.assignment_id=? AND member.writer_profile_id=wp.id
          AND wp.user_id=? AND member.status='OFFERED'
        """, memberStatus, note, assignmentId, user.userId());
    if (updated == 0) return false;
    int writerCount = ((Number) assignment.get("writerCount")).intValue();
    String assignmentStatus = refreshWritingAssignmentStatus(assignmentId, writerCount);
    jdbc.update("""
        UPDATE writing_assignment
        SET responded_at=CURRENT_TIMESTAMP, response_note=?
        WHERE id=?
        """, note, assignmentId);
    if ("ACCEPTED".equals(assignmentStatus)) {
      jdbc.update("""
          UPDATE editorial_task et
          SET assigned_operator_id=CASE WHEN wa.writer_count=1 THEN ? ELSE et.assigned_operator_id END,
              status='PENDING_EXECUTION'
          FROM writing_assignment wa
          WHERE wa.id=? AND et.id=wa.editorial_task_id
          """, user.userId(), assignmentId);
    } else {
      jdbc.update("""
          UPDATE editorial_task et
          SET status='PENDING_ASSIGNMENT'
          FROM writing_assignment wa
          WHERE wa.id=? AND et.id=wa.editorial_task_id
          """, assignmentId);
    }
    log(user, "ACCEPT".equals(decision) ? "ACCEPT_WRITING_ASSIGNMENT" : "DECLINE_WRITING_ASSIGNMENT",
        "WRITING_ASSIGNMENT", String.valueOf(assignmentId),
        Map.of("decision", decision, "assignmentStatus", assignmentStatus));
    return true;
  }

  private Map<String, Object> writingAssignmentForUpdate(Long assignmentId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        SELECT wa.id, wa.status, wa.writer_count AS "writerCount", r.event_time AS "eventTime"
        FROM writing_assignment wa
        JOIN editorial_task et ON et.id=wa.editorial_task_id
        JOIN customer_requirement r ON r.id=et.requirement_id
        WHERE wa.id=?
        FOR UPDATE
        """, assignmentId);
    return rows.isEmpty() ? Map.of() : rows.get(0);
  }

  private long activeWritingAssignmentSeatCount(Long assignmentId) {
    return count("""
        SELECT count(*) FROM writing_assignment_member
        WHERE assignment_id=? AND status IN ('OFFERED','ACCEPTED')
        """, assignmentId);
  }

  private WritingAssignmentOfferOutcome writingAssignmentWriterEligibility(
      Long writerProfileId, BigDecimal distanceKm) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        SELECT service_radius_km
        FROM writer_profile
        WHERE id=? AND status='ACTIVE' AND availability_status='AVAILABLE'
        """, writerProfileId);
    if (rows.isEmpty()) return WritingAssignmentOfferOutcome.WRITER_UNAVAILABLE;

    Object rawRadius = rows.get(0).get("service_radius_km");
    BigDecimal serviceRadiusKm = rawRadius == null ? null : new BigDecimal(rawRadius.toString());
    if (serviceRadiusKm == null) return null;
    if (distanceKm == null) return WritingAssignmentOfferOutcome.DISTANCE_REQUIRED;
    if (distanceKm.compareTo(serviceRadiusKm) > 0) {
      return WritingAssignmentOfferOutcome.OUT_OF_SERVICE_RADIUS;
    }
    return null;
  }

  private boolean writingAssignmentHasWriter(Long assignmentId, Long writerProfileId) {
    return count("""
        SELECT count(*) FROM writing_assignment_member
        WHERE assignment_id=? AND writer_profile_id=?
        """, assignmentId, writerProfileId) > 0;
  }

  private boolean writerHasConfirmedScheduleConflict(Long assignmentId, Long writerProfileId) {
    return count("""
        SELECT count(*)
        FROM writing_assignment_member confirmed_member
        JOIN writing_assignment candidate_assignment ON candidate_assignment.id=?
        JOIN editorial_task candidate_task ON candidate_task.id=candidate_assignment.editorial_task_id
        JOIN customer_requirement candidate_requirement ON candidate_requirement.id=candidate_task.requirement_id
        WHERE confirmed_member.writer_profile_id=?
          AND confirmed_member.status='ACCEPTED'
          AND confirmed_member.service_window && tstzrange(
            candidate_requirement.event_time,
            candidate_requirement.event_time + (candidate_assignment.service_days * INTERVAL '1 day'),
            '[)'
          )
        """, assignmentId, writerProfileId) > 0;
  }

  private String refreshWritingAssignmentStatus(Long assignmentId, int writerCount) {
    long accepted = count("""
        SELECT count(*) FROM writing_assignment_member
        WHERE assignment_id=? AND status='ACCEPTED'
        """, assignmentId);
    long offered = count("""
        SELECT count(*) FROM writing_assignment_member
        WHERE assignment_id=? AND status='OFFERED'
        """, assignmentId);
    String nextStatus = accepted >= writerCount ? "ACCEPTED"
        : accepted > 0 ? "PARTIALLY_ACCEPTED"
        : offered > 0 ? "OFFERED"
        : "WAITING_MATCH";
    jdbc.update("UPDATE writing_assignment SET status=? WHERE id=?", nextStatus, assignmentId);
    return nextStatus;
  }

  @Transactional
  public Long createChannel(AuthPrincipal user, CreateChannelRequest request) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    String channelNo = no("CH");
    jdbc.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO publish_channel
          (channel_no, channel_name, channel_type, category, region, publish_form, expected_days, link_support, public_notes, status)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, channelNo); ps.setString(2, request.channelName()); ps.setString(3, request.channelType());
      ps.setString(4, request.category()); ps.setString(5, request.region()); ps.setString(6, request.publishForm());
      ps.setObject(7, request.expectedDays()); ps.setBoolean(8, request.linkSupport() == null || request.linkSupport());
      ps.setString(9, request.publicNotes()); return ps;
    }, keyHolder);
    Long channelId = key(keyHolder);
    if (request.customerPrice() != null && request.validUntil() != null) {
      jdbc.update("""
          INSERT INTO channel_quote
          (quote_no, channel_id, customer_tier, cost_price, customer_price, currency, valid_from, valid_until, public_terms, status)
          VALUES (?, ?, 'STANDARD', ?, ?, 'CNY', CURRENT_TIMESTAMP, ?, '提交后复核稿件、栏目和排期。', 'ACTIVE')
          """, no("QUO"), channelId, request.costPrice(), request.customerPrice(), request.validUntil());
    }
    if ("DIRECT_PUBLISHING".equals(request.channelType())) {
      jdbc.update("""
          INSERT INTO publish_offering (offering_no, channel_id, offering_name, status)
          VALUES (?, ?, ?, 'ACTIVE') ON CONFLICT (channel_id) DO NOTHING
          """, no("OFF"), channelId, request.channelName());
    }
    log(user, "CREATE_CHANNEL", "CHANNEL", channelNo, Map.of("channelType", request.channelType()));
    return channelId;
  }

  public List<Map<String, Object>> adminChannels(
      String type, String status, String keyword, int limit, int offset) {
    StringBuilder sql = new StringBuilder("""
        SELECT c.id, c.channel_no AS "channelNo", c.channel_name AS "channelName",
               c.channel_type AS "channelType", c.category, c.region,
               c.publish_form AS "publishForm", c.expected_days AS "expectedDays",
               c.link_support AS "linkSupport", c.public_notes AS "publicNotes", c.status,
               q.cost_price AS "costPrice", q.customer_price AS "customerPrice", q.currency,
               q.valid_until AS "validUntil", q.public_terms AS "publicTerms"
        FROM publish_channel c
        LEFT JOIN LATERAL (
          SELECT cost_price, customer_price, currency, valid_until, public_terms
          FROM channel_quote WHERE channel_id=c.id ORDER BY created_at DESC, id DESC LIMIT 1
        ) q ON TRUE
        WHERE 1=1
        """);
    List<Object> args = new ArrayList<>();
    if (type != null && !type.isBlank()) { sql.append(" AND c.channel_type=?"); args.add(type); }
    if (status != null && !status.isBlank()) { sql.append(" AND c.status=?"); args.add(status); }
    if (keyword != null && !keyword.isBlank()) {
      sql.append(" AND (c.channel_name ILIKE ? OR c.channel_no ILIKE ? OR c.category ILIKE ?)");
      String value = "%" + keyword + "%";
      args.add(value); args.add(value); args.add(value);
    }
    sql.append(" ORDER BY c.updated_at DESC, c.id DESC LIMIT ? OFFSET ?");
    args.add(limit); args.add(offset);
    return jdbc.queryForList(sql.toString(), args.toArray());
  }

  public long adminChannelsCount(String type, String status, String keyword) {
    StringBuilder sql = new StringBuilder("SELECT count(*) FROM publish_channel c WHERE 1=1");
    List<Object> args = new ArrayList<>();
    if (type != null && !type.isBlank()) { sql.append(" AND c.channel_type=?"); args.add(type); }
    if (status != null && !status.isBlank()) { sql.append(" AND c.status=?"); args.add(status); }
    if (keyword != null && !keyword.isBlank()) {
      sql.append(" AND (c.channel_name ILIKE ? OR c.channel_no ILIKE ? OR c.category ILIKE ?)");
      String value = "%" + keyword + "%";
      args.add(value); args.add(value); args.add(value);
    }
    return count(sql.toString(), args.toArray());
  }

  public List<Map<String, Object>> pricingChannels(
      String keyword, String region, String category, String publishForm, String channelStatus,
      String quoteState, int limit, int offset) {
    StringBuilder sql = new StringBuilder("""
        SELECT c.id, c.channel_no AS "channelNo", c.channel_name AS "channelName", c.category, c.region,
               c.publish_form AS "publishForm", c.expected_days AS "expectedDays", c.status AS "channelStatus",
               q.id AS "quoteId", q.cost_price AS "costPrice", q.customer_price AS "customerPrice",
               q.currency, q.valid_from AS "validFrom",
               q.valid_until AS "validUntil", q.public_terms AS "publicTerms", q.status AS "quoteStatus",
               q.supplier_id AS "supplierId", s.supplier_name AS "supplierName",
               q.created_at AS "quoteCreatedAt",
               CASE
                 WHEN q.id IS NULL THEN 'UNQUOTED'
                 WHEN q.status <> 'ACTIVE' OR q.valid_until <= CURRENT_TIMESTAMP THEN 'EXPIRED'
                 WHEN q.valid_until <= CURRENT_TIMESTAMP + INTERVAL '7 days' THEN 'EXPIRING'
                 ELSE 'ACTIVE'
               END AS "quoteState"
        FROM publish_channel c
        LEFT JOIN LATERAL (
          SELECT id, supplier_id, cost_price, customer_price, currency, valid_from, valid_until, public_terms, status, created_at
          FROM channel_quote WHERE channel_id=c.id ORDER BY created_at DESC, id DESC LIMIT 1
        ) q ON TRUE
        LEFT JOIN supplier s ON s.id=q.supplier_id
        WHERE c.channel_type='DIRECT_PUBLISHING'
        """);
    List<Object> args = new ArrayList<>();
    if (channelStatus != null && !channelStatus.isBlank()) { sql.append(" AND c.status=?"); args.add(channelStatus); }
    if (region != null && !region.isBlank()) { sql.append(" AND c.region ILIKE ?"); args.add("%" + region.trim() + "%"); }
    if (category != null && !category.isBlank()) { sql.append(" AND c.category ILIKE ?"); args.add("%" + category.trim() + "%"); }
    if (publishForm != null && !publishForm.isBlank()) { sql.append(" AND c.publish_form ILIKE ?"); args.add("%" + publishForm.trim() + "%"); }
    if (keyword != null && !keyword.isBlank()) {
      String value = "%" + keyword.trim() + "%";
      sql.append(" AND (c.channel_name ILIKE ? OR c.channel_no ILIKE ? OR c.category ILIKE ?)");
      args.add(value); args.add(value); args.add(value);
    }
    appendQuoteStateFilter(sql, quoteState);
    sql.append(" ORDER BY CASE WHEN q.id IS NULL THEN 1 ELSE 0 END, q.valid_until ASC NULLS LAST, c.channel_name LIMIT ? OFFSET ?");
    args.add(limit); args.add(offset);
    return jdbc.queryForList(sql.toString(), args.toArray());
  }

  public long pricingChannelsCount(
      String keyword, String region, String category, String publishForm, String channelStatus, String quoteState) {
    StringBuilder sql = new StringBuilder("""
        SELECT count(*) FROM publish_channel c
        LEFT JOIN LATERAL (
          SELECT id, valid_until, status, created_at
          FROM channel_quote WHERE channel_id=c.id ORDER BY created_at DESC, id DESC LIMIT 1
        ) q ON TRUE
        WHERE c.channel_type='DIRECT_PUBLISHING'
        """);
    List<Object> args = new ArrayList<>();
    if (channelStatus != null && !channelStatus.isBlank()) { sql.append(" AND c.status=?"); args.add(channelStatus); }
    if (region != null && !region.isBlank()) { sql.append(" AND c.region ILIKE ?"); args.add("%" + region.trim() + "%"); }
    if (category != null && !category.isBlank()) { sql.append(" AND c.category ILIKE ?"); args.add("%" + category.trim() + "%"); }
    if (publishForm != null && !publishForm.isBlank()) { sql.append(" AND c.publish_form ILIKE ?"); args.add("%" + publishForm.trim() + "%"); }
    if (keyword != null && !keyword.isBlank()) {
      String value = "%" + keyword.trim() + "%";
      sql.append(" AND (c.channel_name ILIKE ? OR c.channel_no ILIKE ? OR c.category ILIKE ?)");
      args.add(value); args.add(value); args.add(value);
    }
    appendQuoteStateFilter(sql, quoteState);
    return count(sql.toString(), args.toArray());
  }

  private void appendQuoteStateFilter(StringBuilder sql, String quoteState) {
    if (quoteState == null || quoteState.isBlank()) return;
    switch (quoteState) {
      case "ACTIVE" -> sql.append(" AND q.id IS NOT NULL AND q.status='ACTIVE' AND q.valid_until>CURRENT_TIMESTAMP + INTERVAL '7 days'");
      case "EXPIRING" -> sql.append(" AND q.id IS NOT NULL AND q.status='ACTIVE' AND q.valid_until>CURRENT_TIMESTAMP AND q.valid_until<=CURRENT_TIMESTAMP + INTERVAL '7 days'");
      case "EXPIRED" -> sql.append(" AND q.id IS NOT NULL AND (q.status<>'ACTIVE' OR q.valid_until<=CURRENT_TIMESTAMP)");
      case "UNQUOTED" -> sql.append(" AND q.id IS NULL");
      default -> throw new IllegalArgumentException("Unsupported quote state");
    }
  }

  public Map<String, Object> pricingSummary() {
    return one("""
        SELECT count(*) AS "totalChannels",
               count(*) FILTER (WHERE q.id IS NOT NULL AND q.status='ACTIVE' AND q.valid_until>CURRENT_TIMESTAMP + INTERVAL '7 days') AS "activeQuotes",
               count(*) FILTER (WHERE q.id IS NOT NULL AND q.status='ACTIVE' AND q.valid_until>CURRENT_TIMESTAMP AND q.valid_until<=CURRENT_TIMESTAMP + INTERVAL '7 days') AS "expiringQuotes",
               count(*) FILTER (WHERE q.id IS NOT NULL AND (q.status<>'ACTIVE' OR q.valid_until<=CURRENT_TIMESTAMP)) AS "expiredQuotes",
               count(*) FILTER (WHERE q.id IS NULL) AS "unquotedChannels"
        FROM publish_channel c
        LEFT JOIN LATERAL (
          SELECT id, valid_until, status, created_at FROM channel_quote
          WHERE channel_id=c.id ORDER BY created_at DESC, id DESC LIMIT 1
        ) q ON TRUE
        WHERE c.channel_type='DIRECT_PUBLISHING'
        """);
  }

  public List<Map<String, Object>> pricingComparison(List<Long> channelIds) {
    String placeholders = String.join(",", java.util.Collections.nCopies(channelIds.size(), "?"));
    return jdbc.queryForList("""
        SELECT c.id, c.channel_no AS "channelNo", c.channel_name AS "channelName", c.category, c.region,
               c.publish_form AS "publishForm", c.expected_days AS "expectedDays", c.status AS "channelStatus",
               q.cost_price AS "costPrice", q.customer_price AS "customerPrice", q.currency,
               q.valid_until AS "validUntil", q.supplier_id AS "supplierId",
               s.supplier_name AS "supplierName",
               CASE
                 WHEN q.id IS NULL THEN 'UNQUOTED'
                 WHEN q.status <> 'ACTIVE' OR q.valid_until <= CURRENT_TIMESTAMP THEN 'EXPIRED'
                 WHEN q.valid_until <= CURRENT_TIMESTAMP + INTERVAL '7 days' THEN 'EXPIRING'
                 ELSE 'ACTIVE'
               END AS "quoteState"
        FROM publish_channel c
        LEFT JOIN LATERAL (
          SELECT id, supplier_id, cost_price, customer_price, currency, valid_until, status, created_at FROM channel_quote
          WHERE channel_id=c.id ORDER BY created_at DESC, id DESC LIMIT 1
        ) q ON TRUE
        LEFT JOIN supplier s ON s.id=q.supplier_id
        WHERE c.channel_type='DIRECT_PUBLISHING' AND c.id IN (""" + placeholders + ") ORDER BY c.channel_name", channelIds.toArray());
  }

  public Map<String, Object> pricingChannel(Long channelId) {
    return one("""
        SELECT c.id, c.channel_no AS "channelNo", c.channel_type AS "channelType", c.status AS "channelStatus",
               q.id AS "quoteId", q.cost_price AS "costPrice", q.customer_price AS "customerPrice",
               q.valid_until AS "validUntil", q.supplier_id AS "supplierId"
        FROM publish_channel c
        LEFT JOIN LATERAL (
          SELECT id, supplier_id, cost_price, customer_price, valid_until, created_at FROM channel_quote
          WHERE channel_id=c.id ORDER BY created_at DESC, id DESC LIMIT 1
        ) q ON TRUE
        WHERE c.id=?
        """, channelId);
  }

  public void lockPricingChannelForUpdate(Long channelId) {
    jdbc.queryForList(
        "SELECT id FROM publish_channel WHERE id=? FOR UPDATE",
        Long.class,
        channelId);
  }

  public Map<String, Object> lockOrCreateQuoteAdjustmentBatch(
      AuthPrincipal user,
      String submissionKey,
      String submissionHash,
      BigDecimal percentage,
      OffsetDateTime validUntil,
      String publicTerms,
      String reason,
      int channelCount) {
    int inserted = jdbc.update("""
        INSERT INTO quote_adjustment_batch
        (batch_no, adjusted_by, submission_key, submission_hash, percentage,
         valid_until, public_terms, reason, channel_count, adjusted_count, status)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'PROCESSING')
        ON CONFLICT (adjusted_by, submission_key) DO NOTHING
        """, no("QAB"), user.userId(), submissionKey, submissionHash, percentage,
        validUntil, publicTerms, reason, channelCount);
    Map<String, Object> stored = one("""
        SELECT id, batch_no AS "batchNo", submission_hash AS "submissionHash",
               channel_count AS "channelCount", adjusted_count AS "adjustedCount", status
        FROM quote_adjustment_batch
        WHERE adjusted_by=? AND submission_key=?
        FOR UPDATE
        """, user.userId(), submissionKey);
    if (stored.isEmpty()) {
      throw new BusinessException(
          "BATCH_ADJUSTMENT_STATE_UNAVAILABLE",
          "批量调价状态暂时不可用，请稍后重试",
          HttpStatus.CONFLICT);
    }
    Map<String, Object> result = new LinkedHashMap<>(stored);
    result.put("created", inserted > 0);
    return result;
  }

  public List<Map<String, Object>> quoteAdjustmentBatchItems(Long batchId) {
    return jdbc.queryForList("""
        SELECT a.channel_id AS "channelId", a.current_quote_id AS "quoteId",
               q.supplier_id AS "supplierId", a.current_cost_price AS "costPrice",
               a.current_customer_price AS "customerPrice", q.valid_until AS "validUntil"
        FROM quote_adjustment a
        JOIN channel_quote q ON q.id=a.current_quote_id
        WHERE a.batch_id=?
        ORDER BY a.channel_id
        """, batchId);
  }

  public void completeQuoteAdjustmentBatch(
      AuthPrincipal user, Long batchId, int adjustedCount) {
    int updated = jdbc.update("""
        UPDATE quote_adjustment_batch
        SET adjusted_count=?, status='COMPLETED'
        WHERE id=? AND status='PROCESSING' AND adjusted_count=0
        """, adjustedCount, batchId);
    if (updated != 1) {
      throw new BusinessException(
          "BATCH_ADJUSTMENT_INCOMPLETE",
          "该批调价未能完整提交，请暂停继续调价并联系系统管理员",
          HttpStatus.CONFLICT);
    }
    log(user, "BATCH_ADJUST_CHANNEL_PRICE", "QUOTE_ADJUSTMENT_BATCH",
        String.valueOf(batchId), Map.of("adjustedCount", adjustedCount));
  }

  @Transactional
  public Map<String, Object> replaceDirectQuote(
      AuthPrincipal user, Long channelId, Long supplierId, BigDecimal costPrice,
      BigDecimal customerPrice, OffsetDateTime validUntil, String publicTerms, String reason, String mode) {
    return replaceDirectQuote(
        user, channelId, supplierId, costPrice, customerPrice, validUntil,
        publicTerms, reason, mode, null);
  }

  @Transactional
  public Map<String, Object> replaceDirectQuote(
      AuthPrincipal user, Long channelId, Long supplierId, BigDecimal costPrice,
      BigDecimal customerPrice, OffsetDateTime validUntil, String publicTerms, String reason,
      String mode, Long batchId) {
    Map<String, Object> previous = one("""
        SELECT id, supplier_id AS "supplierId", customer_price AS "customerPrice", cost_price AS "costPrice"
        FROM channel_quote WHERE channel_id=? ORDER BY created_at DESC, id DESC LIMIT 1 FOR UPDATE
        """, channelId);
    BigDecimal nextCostPrice = costPrice != null
        ? costPrice
        : previous.get("costPrice") instanceof BigDecimal previousCost ? previousCost : null;
    Long nextSupplierId = supplierId != null
        ? supplierId
        : previous.get("supplierId") instanceof Number previousSupplier
            ? previousSupplier.longValue() : null;
    jdbc.update("UPDATE channel_quote SET status='SUPERSEDED' WHERE channel_id=? AND status='ACTIVE'", channelId);
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO channel_quote
          (quote_no, channel_id, supplier_id, customer_tier, cost_price, customer_price,
           currency, valid_from, valid_until, public_terms, status)
          VALUES (?, ?, ?, 'STANDARD', ?, ?, 'CNY', CURRENT_TIMESTAMP, ?, ?, 'ACTIVE')
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, no("QUO"));
      ps.setLong(2, channelId);
      ps.setObject(3, nextSupplierId, java.sql.Types.BIGINT);
      ps.setBigDecimal(4, nextCostPrice);
      ps.setBigDecimal(5, customerPrice);
      ps.setObject(6, validUntil);
      ps.setString(7, publicTerms == null || publicTerms.isBlank()
          ? "提交后复核稿件、栏目和排期。" : publicTerms.trim());
      return ps;
    }, keyHolder);
    Long quoteId = key(keyHolder);
    jdbc.update("""
        INSERT INTO quote_adjustment
        (adjustment_no, channel_id, previous_quote_id, current_quote_id,
         previous_cost_price, current_cost_price, previous_customer_price,
         current_customer_price, adjustment_mode, reason, adjusted_by, batch_id)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, no("QAD"), channelId, previous.get("id"), quoteId,
        previous.get("costPrice"), nextCostPrice, previous.get("customerPrice"), customerPrice,
        mode, reason.trim(), user.userId(), batchId);
    log(user, "ADJUST_CHANNEL_PRICE", "CHANNEL", String.valueOf(channelId),
        Map.of("mode", mode, "reason", reason.trim(), "quoteId", quoteId));
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("channelId", channelId);
    result.put("quoteId", quoteId);
    result.put("supplierId", nextSupplierId);
    result.put("costPrice", nextCostPrice);
    result.put("customerPrice", customerPrice);
    result.put("validUntil", validUntil);
    return result;
  }

  public List<Map<String, Object>> quoteAdjustments(Long channelId, int limit) {
    return jdbc.queryForList("""
        SELECT a.adjustment_no AS "adjustmentNo", a.previous_customer_price AS "previousCustomerPrice",
               a.current_customer_price AS "currentCustomerPrice",
               a.previous_cost_price AS "previousCostPrice", a.current_cost_price AS "currentCostPrice",
               a.adjustment_mode AS "adjustmentMode",
               a.reason, a.created_at AS "createdAt", u.display_name AS "adjustedBy"
        FROM quote_adjustment a LEFT JOIN app_user u ON u.id=a.adjusted_by
        WHERE a.channel_id=? ORDER BY a.created_at DESC, a.id DESC LIMIT ?
        """, channelId, limit);
  }

  public List<Map<String, Object>> suppliers(
      String type, String status, String keyword, int limit, int offset) {
    StringBuilder sql = new StringBuilder("""
        SELECT s.id, s.supplier_no AS "supplierNo", s.supplier_name AS "supplierName",
               s.supplier_type AS "supplierType", s.contact_name AS "contactName",
               s.contact_phone AS "contactPhone", s.contact_email AS "contactEmail",
               s.service_scope AS "serviceScope", s.internal_note AS "internalNote", s.status,
               s.created_at AS "createdAt", s.updated_at AS "updatedAt",
               (SELECT count(*) FROM supplier_channel sc
                 WHERE sc.supplier_id=s.id AND sc.status='ACTIVE') AS "channelCount",
               (SELECT count(*) FROM supplier_order so
                 WHERE so.supplier_id=s.id
                   AND so.status NOT IN ('COMPLETED','CANCELLED')) AS "activeOrderCount"
        FROM supplier s WHERE 1=1
        """);
    List<Object> args = new ArrayList<>();
    if (!blank(type)) { sql.append(" AND s.supplier_type=?"); args.add(type); }
    if (!blank(status)) { sql.append(" AND s.status=?"); args.add(status); }
    if (!blank(keyword)) {
      sql.append(" AND (s.supplier_name ILIKE ? OR s.supplier_no ILIKE ? OR s.contact_name ILIKE ?)");
      String value = "%" + keyword.trim() + "%";
      args.add(value); args.add(value); args.add(value);
    }
    sql.append(" ORDER BY s.status, s.updated_at DESC, s.id DESC LIMIT ? OFFSET ?");
    args.add(limit); args.add(offset);
    return jdbc.queryForList(sql.toString(), args.toArray());
  }

  public long suppliersCount(String type, String status, String keyword) {
    StringBuilder sql = new StringBuilder("SELECT count(*) FROM supplier s WHERE 1=1");
    List<Object> args = new ArrayList<>();
    if (!blank(type)) { sql.append(" AND s.supplier_type=?"); args.add(type); }
    if (!blank(status)) { sql.append(" AND s.status=?"); args.add(status); }
    if (!blank(keyword)) {
      sql.append(" AND (s.supplier_name ILIKE ? OR s.supplier_no ILIKE ? OR s.contact_name ILIKE ?)");
      String value = "%" + keyword.trim() + "%";
      args.add(value); args.add(value); args.add(value);
    }
    return count(sql.toString(), args.toArray());
  }

  public List<Map<String, Object>> supplierOptions() {
    return jdbc.queryForList("""
        SELECT id, supplier_no AS "supplierNo", supplier_name AS "supplierName",
               supplier_type AS "supplierType"
        FROM supplier WHERE status='ACTIVE' ORDER BY supplier_name
        """);
  }

  public List<Map<String, Object>> supplierOptionsForChannel(Long channelId) {
    return jdbc.queryForList("""
        SELECT s.id, s.supplier_no AS "supplierNo", s.supplier_name AS "supplierName",
               s.supplier_type AS "supplierType"
        FROM supplier s
        JOIN supplier_channel sc ON sc.supplier_id=s.id
        WHERE sc.channel_id=? AND sc.status='ACTIVE' AND s.status='ACTIVE'
        ORDER BY sc.priority, s.supplier_name
        """, channelId);
  }

  public boolean activeSupplierExists(Long supplierId) {
    return count("SELECT count(*) FROM supplier WHERE id=? AND status='ACTIVE'", supplierId) > 0;
  }

  public boolean activeSupplierCanServeChannel(Long supplierId, Long channelId) {
    return count("""
        SELECT count(*) FROM supplier s
        JOIN supplier_channel sc ON sc.supplier_id=s.id
        WHERE s.id=? AND sc.channel_id=? AND s.status='ACTIVE' AND sc.status='ACTIVE'
        """, supplierId, channelId) > 0;
  }

  public boolean channelExists(Long channelId) {
    return count("SELECT count(*) FROM publish_channel WHERE id=?", channelId) > 0;
  }

  public Long createSupplier(AuthPrincipal user, CreateSupplierRequest request) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    String supplierNo = no("SUP");
    jdbc.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO supplier
          (supplier_no, supplier_name, supplier_type, contact_name, contact_phone,
           contact_email, service_scope, internal_note, status)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, supplierNo);
      ps.setString(2, request.supplierName().trim());
      ps.setString(3, request.supplierType());
      ps.setString(4, blankToNull(request.contactName()));
      ps.setString(5, blankToNull(request.contactPhone()));
      ps.setString(6, blankToNull(request.contactEmail()));
      ps.setString(7, blankToNull(request.serviceScope()));
      ps.setString(8, blankToNull(request.internalNote()));
      return ps;
    }, keyHolder);
    Long supplierId = key(keyHolder);
    log(user, "CREATE_SUPPLIER", "SUPPLIER", supplierNo,
        Map.of("supplierId", supplierId, "supplierType", request.supplierType()));
    return supplierId;
  }

  public boolean updateSupplier(
      AuthPrincipal user, Long supplierId, UpdateSupplierRequest request) {
    int updated = jdbc.update("""
        UPDATE supplier
        SET supplier_name=?, supplier_type=?, contact_name=?, contact_phone=?,
            contact_email=?, service_scope=?, internal_note=?, status=?
        WHERE id=?
        """, request.supplierName().trim(), request.supplierType(),
        blankToNull(request.contactName()), blankToNull(request.contactPhone()),
        blankToNull(request.contactEmail()), blankToNull(request.serviceScope()),
        blankToNull(request.internalNote()), request.status(), supplierId);
    if (updated > 0) {
      log(user, "UPDATE_SUPPLIER", "SUPPLIER", String.valueOf(supplierId),
          Map.of("supplierType", request.supplierType(), "status", request.status()));
    }
    return updated > 0;
  }

  public List<Map<String, Object>> supplierChannels(
      Long supplierId, Long channelId, int limit, int offset) {
    StringBuilder sql = new StringBuilder("""
        SELECT sc.id, sc.mapping_no AS "mappingNo", sc.supplier_id AS "supplierId",
               s.supplier_no AS "supplierNo", s.supplier_name AS "supplierName",
               sc.channel_id AS "channelId", c.channel_no AS "channelNo",
               c.channel_name AS "channelName", c.channel_type AS "channelType",
               sc.external_product_code AS "externalProductCode",
               sc.service_scope AS "serviceScope", sc.priority, sc.status,
               sc.updated_at AS "updatedAt"
        FROM supplier_channel sc
        JOIN supplier s ON s.id=sc.supplier_id
        JOIN publish_channel c ON c.id=sc.channel_id
        WHERE 1=1
        """);
    List<Object> args = new ArrayList<>();
    if (supplierId != null) { sql.append(" AND sc.supplier_id=?"); args.add(supplierId); }
    if (channelId != null) { sql.append(" AND sc.channel_id=?"); args.add(channelId); }
    sql.append(" ORDER BY sc.priority, s.supplier_name, c.channel_name LIMIT ? OFFSET ?");
    args.add(limit); args.add(offset);
    return jdbc.queryForList(sql.toString(), args.toArray());
  }

  public long supplierChannelsCount(Long supplierId, Long channelId) {
    StringBuilder sql = new StringBuilder("SELECT count(*) FROM supplier_channel sc WHERE 1=1");
    List<Object> args = new ArrayList<>();
    if (supplierId != null) { sql.append(" AND sc.supplier_id=?"); args.add(supplierId); }
    if (channelId != null) { sql.append(" AND sc.channel_id=?"); args.add(channelId); }
    return count(sql.toString(), args.toArray());
  }

  public void assignSupplierChannel(AuthPrincipal user, AssignSupplierChannelRequest request) {
    jdbc.update("""
        INSERT INTO supplier_channel
        (mapping_no, supplier_id, channel_id, external_product_code, service_scope, priority, status)
        VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')
        ON CONFLICT (supplier_id, channel_id) DO UPDATE
        SET external_product_code=EXCLUDED.external_product_code,
            service_scope=EXCLUDED.service_scope,
            priority=EXCLUDED.priority,
            status='ACTIVE'
        """, no("SUP-CH"), request.supplierId(), request.channelId(),
        blankToNull(request.externalProductCode()), blankToNull(request.serviceScope()),
        request.priority() == null ? 100 : request.priority());
    log(user, "ASSIGN_SUPPLIER_CHANNEL", "CHANNEL", String.valueOf(request.channelId()),
        Map.of("supplierId", request.supplierId()));
  }

  public List<Map<String, Object>> supplierOrders(
      String status, Long supplierId, String keyword, int limit, int offset) {
    StringBuilder sql = new StringBuilder("""
        SELECT so.id, so.supplier_order_no AS "supplierOrderNo",
               so.supplier_id AS "supplierId", s.supplier_no AS "supplierNo",
               COALESCE(s.supplier_name, '待分配供应商') AS "supplierName",
               so.channel_id AS "channelId",
               so.publish_plan_id AS "publishPlanId", pp.plan_no AS "planNo",
               so.publish_task_id AS "publishTaskId", t.task_no AS "taskNo",
               p.project_no AS "projectNo", p.project_name AS "projectName",
               c.channel_no AS "channelNo", c.channel_name AS "channelName",
               c.channel_type AS "channelType",
               so.customer_price_snapshot AS "customerPrice",
               so.cost_price_snapshot AS "costPrice",
               CASE
                 WHEN so.customer_price_snapshot IS NULL OR so.cost_price_snapshot IS NULL THEN NULL
                 ELSE so.customer_price_snapshot-so.cost_price_snapshot
               END AS "grossMargin",
               so.currency, so.article_title AS "articleTitle",
               so.planned_publish_at AS "plannedPublishAt",
               so.external_order_no AS "externalOrderNo",
               so.submission_note AS "submissionNote",
               so.fulfillment_mode AS "fulfillmentMode",
               so.submission_evidence_ref AS "submissionEvidenceReference",
               so.exception_reason AS "exceptionReason", so.status,
               so.submitted_at AS "submittedAt", so.accepted_at AS "acceptedAt",
               so.completed_at AS "completedAt", so.cancelled_at AS "cancelledAt",
               op.display_name AS "operatorName",
               so.created_at AS "createdAt", so.updated_at AS "updatedAt"
        FROM supplier_order so
        LEFT JOIN supplier s ON s.id=so.supplier_id
        JOIN publish_plan pp ON pp.id=so.publish_plan_id
        JOIN publish_task t ON t.id=so.publish_task_id
        JOIN project p ON p.id=t.project_id
        JOIN publish_channel c ON c.id=so.channel_id
        LEFT JOIN app_user op ON op.id=so.assigned_operator_id
        WHERE 1=1
        """);
    List<Object> args = new ArrayList<>();
    if (!blank(status)) { sql.append(" AND so.status=?"); args.add(status); }
    if (supplierId != null) { sql.append(" AND so.supplier_id=?"); args.add(supplierId); }
    if (!blank(keyword)) {
      String value = "%" + keyword.trim() + "%";
      sql.append("""
           AND (so.supplier_order_no ILIKE ? OR so.external_order_no ILIKE ?
             OR t.task_no ILIKE ? OR pp.plan_no ILIKE ? OR p.project_no ILIKE ?
             OR p.project_name ILIKE ? OR c.channel_name ILIKE ? OR s.supplier_name ILIKE ?)
          """);
      args.add(value); args.add(value); args.add(value); args.add(value);
      args.add(value); args.add(value); args.add(value); args.add(value);
    }
    sql.append(" ORDER BY so.updated_at DESC, so.id DESC LIMIT ? OFFSET ?");
    args.add(limit); args.add(offset);
    return jdbc.queryForList(sql.toString(), args.toArray());
  }

  public long supplierOrdersCount(String status, Long supplierId, String keyword) {
    StringBuilder sql = new StringBuilder("""
        SELECT count(*) FROM supplier_order so
        LEFT JOIN supplier s ON s.id=so.supplier_id
        JOIN publish_plan pp ON pp.id=so.publish_plan_id
        JOIN publish_task t ON t.id=so.publish_task_id
        JOIN project p ON p.id=t.project_id
        JOIN publish_channel c ON c.id=so.channel_id
        WHERE 1=1
        """);
    List<Object> args = new ArrayList<>();
    if (!blank(status)) { sql.append(" AND so.status=?"); args.add(status); }
    if (supplierId != null) { sql.append(" AND so.supplier_id=?"); args.add(supplierId); }
    if (!blank(keyword)) {
      String value = "%" + keyword.trim() + "%";
      sql.append("""
           AND (so.supplier_order_no ILIKE ? OR so.external_order_no ILIKE ?
             OR t.task_no ILIKE ? OR pp.plan_no ILIKE ? OR p.project_no ILIKE ?
             OR p.project_name ILIKE ? OR c.channel_name ILIKE ? OR s.supplier_name ILIKE ?)
          """);
      args.add(value); args.add(value); args.add(value); args.add(value);
      args.add(value); args.add(value); args.add(value); args.add(value);
    }
    return count(sql.toString(), args.toArray());
  }

  public Map<String, Object> supplierOrderSummary() {
    return one("""
        SELECT count(*) AS "totalOrders",
               count(*) FILTER (WHERE status='PENDING_SUBMISSION') AS "pendingSubmission",
               count(*) FILTER (
                 WHERE status IN ('SUBMITTED','ACCEPTED','IN_PROGRESS')
                   AND fulfillment_mode IN ('MANUAL','API')
                   AND NULLIF(btrim(submission_evidence_ref), '') IS NOT NULL
               ) AS "executingOrders",
               count(*) FILTER (
                 WHERE status IN ('SUBMITTED','ACCEPTED','IN_PROGRESS','COMPLETED')
                   AND (
                     fulfillment_mode='UNCONFIRMED'
                     OR NULLIF(btrim(submission_evidence_ref), '') IS NULL
                   )
               ) AS "unverifiedOrders",
               count(*) FILTER (WHERE status='EXCEPTION') AS "exceptionOrders",
               count(*) FILTER (WHERE status='COMPLETED') AS "completedOrders",
               COALESCE(sum(customer_price_snapshot) FILTER
                 (WHERE status<>'CANCELLED'), 0) AS "customerAmount",
               COALESCE(sum(cost_price_snapshot) FILTER
                 (WHERE status<>'CANCELLED'), 0) AS "costAmount"
        FROM supplier_order
        """);
  }

  public Map<String, Object> supplierOrder(Long supplierOrderId) {
    return one("""
        SELECT id, supplier_id AS "supplierId", channel_id AS "channelId",
               publish_task_id AS "publishTaskId", status,
               fulfillment_mode AS "fulfillmentMode",
               submission_evidence_ref AS "submissionEvidenceReference",
               external_order_no AS "externalOrderNo",
               submission_note AS "submissionNote"
        FROM supplier_order WHERE id=? FOR UPDATE
        """, supplierOrderId);
  }

  public boolean supplierOrderExists(Long supplierOrderId) {
    return count("SELECT count(*) FROM supplier_order WHERE id=?", supplierOrderId) > 0;
  }

  public Map<String, Object> supplierOrderForPublishTask(Long publishTaskId) {
    return one("""
        SELECT id, supplier_id AS "supplierId", status,
               fulfillment_mode AS "fulfillmentMode",
               submission_evidence_ref AS "submissionEvidenceReference"
        FROM supplier_order WHERE publish_task_id=? FOR UPDATE
        """, publishTaskId);
  }

  public List<Map<String, Object>> supplierOrderHistory(Long supplierOrderId) {
    return jdbc.queryForList("""
        SELECT history.history_no AS "historyNo",
               history.previous_status AS "previousStatus",
               history.current_status AS "currentStatus",
               history.note,
               history.created_at AS "createdAt",
               actor.display_name AS "changedByName"
        FROM supplier_order_status_history history
        LEFT JOIN app_user actor ON actor.id=history.changed_by
        WHERE history.supplier_order_id=?
        ORDER BY history.created_at ASC, history.id ASC
        """, supplierOrderId);
  }

  public void updateSupplierOrder(
      AuthPrincipal user, Long supplierOrderId, Long supplierId,
      UpdateSupplierOrderRequest request) {
    Map<String, Object> previous = supplierOrder(supplierOrderId);
    String previousStatus = String.valueOf(previous.get("status"));
    boolean clearingCurrentAttempt = "PENDING_SUBMISSION".equals(request.status());
    boolean clearingExternalOrder = clearingCurrentAttempt
        || !"API".equals(request.fulfillmentMode());
    String historyNote = supplierOrderHistoryNote(previous, request);
    jdbc.update("""
        UPDATE supplier_order
        SET supplier_id=?, status=?,
            external_order_no=CASE WHEN ? THEN NULL ELSE COALESCE(?, external_order_no) END,
            fulfillment_mode=?,
            submission_evidence_ref=CASE WHEN ? THEN NULL ELSE ? END,
            submission_note=CASE WHEN ? THEN NULL ELSE COALESCE(?, submission_note) END,
            exception_reason=CASE WHEN ?='EXCEPTION' THEN ? ELSE NULL END,
            submitted_at=CASE WHEN ? THEN NULL
              WHEN ?='SUBMITTED' THEN COALESCE(submitted_at, CURRENT_TIMESTAMP) ELSE submitted_at END,
            accepted_at=CASE WHEN ? THEN NULL
              WHEN ? IN ('ACCEPTED','IN_PROGRESS','COMPLETED')
              THEN COALESCE(accepted_at, CURRENT_TIMESTAMP) ELSE accepted_at END,
            completed_at=CASE WHEN ?='COMPLETED' THEN COALESCE(completed_at, CURRENT_TIMESTAMP) ELSE NULL END,
            cancelled_at=CASE WHEN ?='CANCELLED' THEN COALESCE(cancelled_at, CURRENT_TIMESTAMP) ELSE NULL END
        WHERE id=?
        """, supplierId, request.status(), clearingExternalOrder,
        blankToNull(request.externalOrderNo()), request.fulfillmentMode(), clearingCurrentAttempt,
        blankToNull(request.submissionEvidenceReference()), clearingCurrentAttempt,
        blankToNull(request.note()), request.status(), blankToNull(request.exceptionReason()),
        clearingCurrentAttempt, request.status(), clearingCurrentAttempt, request.status(),
        request.status(), request.status(), supplierOrderId);
    String taskStatus = switch (request.status()) {
      case "IN_PROGRESS", "COMPLETED" -> "IN_PROGRESS";
      case "EXCEPTION", "CANCELLED" -> "EXCEPTION";
      default -> "PENDING_EXECUTION";
    };
    String taskException = switch (request.status()) {
      case "EXCEPTION" -> request.exceptionReason();
      case "CANCELLED" -> "供应商订单已取消";
      default -> null;
    };
    jdbc.update("""
        UPDATE publish_task
        SET status=?, exception_reason=?,
            execution_note=COALESCE(?, execution_note)
        WHERE id=?
          AND status NOT IN ('COMPLETED','CLIENT_ACCEPTED','NOT_PROCEEDING')
        """, taskStatus, taskException, blankToNull(request.note()),
        previous.get("publishTaskId"));
    syncPublishExecutionProgress(number(previous.get("publishTaskId")));
    jdbc.update("""
        INSERT INTO supplier_order_status_history
        (history_no, supplier_order_id, previous_status, current_status, note, changed_by)
        VALUES (?, ?, ?, ?, ?, ?)
        """, no("SUP-HIS"), supplierOrderId, previousStatus, request.status(),
        historyNote, user.userId());
    log(user, "UPDATE_SUPPLIER_ORDER", "SUPPLIER_ORDER", String.valueOf(supplierOrderId),
        Map.of(
            "previousStatus", previousStatus,
            "status", request.status(),
            "fulfillmentMode", request.fulfillmentMode()));
  }

  public boolean recentBusinessInquiryExists(
      String inquiryType, String mobile, String companyName) {
    return count("""
        SELECT count(*) FROM business_inquiry
        WHERE inquiry_type=? AND mobile=? AND lower(company_name)=lower(?)
          AND created_at>CURRENT_TIMESTAMP-INTERVAL '5 minutes'
        """, inquiryType, mobile.trim(), companyName.trim()) > 0;
  }

  public Long createBusinessInquiry(CreateBusinessInquiryRequest request) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO business_inquiry
          (inquiry_no, inquiry_type, company_name, contact_name, mobile, email, message, status)
          VALUES (?, ?, ?, ?, ?, ?, ?, 'NEW')
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, no("INQ"));
      ps.setString(2, request.inquiryType());
      ps.setString(3, request.companyName().trim());
      ps.setString(4, request.contactName().trim());
      ps.setString(5, request.mobile().trim());
      ps.setString(6, blankToNull(request.email()));
      ps.setString(7, request.message().trim());
      return ps;
    }, keyHolder);
    return key(keyHolder);
  }

  public List<Map<String, Object>> businessInquiries(
      String status, String type, int limit, int offset) {
    StringBuilder sql = new StringBuilder("""
        SELECT i.id, i.inquiry_no AS "inquiryNo", i.inquiry_type AS "inquiryType",
               i.company_name AS "companyName", i.contact_name AS "contactName",
               i.mobile, i.email, i.message, i.status,
               i.handled_by AS "handledBy", u.display_name AS "handlerName",
               i.handled_at AS "handledAt", i.handling_note AS "handlingNote",
               i.created_at AS "createdAt", i.updated_at AS "updatedAt"
        FROM business_inquiry i LEFT JOIN app_user u ON u.id=i.handled_by
        WHERE 1=1
        """);
    List<Object> args = new ArrayList<>();
    if (!blank(status)) { sql.append(" AND i.status=?"); args.add(status); }
    if (!blank(type)) { sql.append(" AND i.inquiry_type=?"); args.add(type); }
    sql.append(" ORDER BY CASE i.status WHEN 'NEW' THEN 1 WHEN 'CONTACTED' THEN 2 ELSE 3 END, i.created_at DESC LIMIT ? OFFSET ?");
    args.add(limit); args.add(offset);
    return jdbc.queryForList(sql.toString(), args.toArray());
  }

  public long businessInquiriesCount(String status, String type) {
    StringBuilder sql = new StringBuilder("SELECT count(*) FROM business_inquiry i WHERE 1=1");
    List<Object> args = new ArrayList<>();
    if (!blank(status)) { sql.append(" AND i.status=?"); args.add(status); }
    if (!blank(type)) { sql.append(" AND i.inquiry_type=?"); args.add(type); }
    return count(sql.toString(), args.toArray());
  }

  public boolean updateBusinessInquiry(
      AuthPrincipal user, Long inquiryId, UpdateBusinessInquiryRequest request) {
    int updated = jdbc.update("""
        UPDATE business_inquiry
        SET status=?, handling_note=COALESCE(?, handling_note),
            handled_by=?, handled_at=CURRENT_TIMESTAMP
        WHERE id=?
        """, request.status(), blankToNull(request.handlingNote()),
        user.userId(), inquiryId);
    if (updated > 0) {
      log(user, "UPDATE_BUSINESS_INQUIRY", "BUSINESS_INQUIRY",
          String.valueOf(inquiryId), Map.of("status", request.status()));
    }
    return updated > 0;
  }

  @Transactional
  public boolean updateChannel(AuthPrincipal user, Long channelId, UpdateChannelRequest request) {
    int updated = jdbc.update("""
        UPDATE publish_channel SET channel_name=?, channel_type=?, category=?, region=?, publish_form=?,
          expected_days=?, link_support=?, public_notes=?, status=? WHERE id=?
    """, request.channelName(), request.channelType(), request.category(), request.region(),
        request.publishForm(), request.expectedDays(), request.linkSupport() == null || request.linkSupport(),
        request.publicNotes(), request.status(), channelId);
    if (updated == 0) return false;
    log(user, "UPDATE_CHANNEL", "CHANNEL", String.valueOf(channelId),
        Map.of("channelType", request.channelType(), "status", request.status()));
    if ("DIRECT_PUBLISHING".equals(request.channelType())) {
      jdbc.update("""
          INSERT INTO publish_offering (offering_no, channel_id, offering_name, status)
          VALUES (?, ?, ?, ?)
          ON CONFLICT (channel_id) DO UPDATE SET offering_name=EXCLUDED.offering_name, status=EXCLUDED.status
          """, no("OFF"), channelId, request.channelName(), request.status());
    } else {
      jdbc.update("UPDATE publish_offering SET status='INACTIVE' WHERE channel_id=?", channelId);
    }
    return true;
  }

  public List<Map<String, Object>> settlements(String status, int limit, int offset) {
    StringBuilder sql = new StringBuilder("""
        SELECT s.id, s.settlement_no AS "settlementNo", p.project_no AS "projectNo", p.project_name AS "projectName",
               o.name AS "organizationName", r.requested_service AS "serviceType",
               CASE r.requested_service
                 WHEN 'ONSITE_WRITING' THEN '云采写'
                 WHEN 'MEDIA_PR' THEN '媒体邀请'
                 WHEN 'DIRECT_PUBLISHING' THEN '直编发稿'
                 WHEN 'NEWS_CONFERENCE' THEN '举办新闻发布会'
                 ELSE '历史组合记录'
               END AS "serviceLabel",
               CASE WHEN r.requested_service IN
                 ('ONSITE_WRITING','MEDIA_PR','DIRECT_PUBLISHING','NEWS_CONFERENCE')
                 THEN FALSE ELSE TRUE END AS "archiveOnly",
               s.amount, s.paid_amount AS "paidAmount", s.currency,
               tx.adjustment_amount AS "adjustmentAmount",
               GREATEST(s.amount + tx.adjustment_amount - s.paid_amount, 0) AS "outstandingAmount",
               s.due_at AS "dueAt", s.paid_at AS "paidAt", s.invoice_no AS "invoiceNo", s.status
        FROM settlement_order s
        JOIN project p ON p.id=s.project_id
        JOIN customer_requirement r ON r.id=p.requirement_id
        JOIN organization o ON o.id=s.organization_id
        LEFT JOIN LATERAL (
          SELECT COALESCE(SUM(
            CASE
              WHEN st.transaction_type='DEBIT_ADJUSTMENT' THEN st.amount
              WHEN st.transaction_type IN ('CREDIT_ADJUSTMENT', 'WRITE_OFF') THEN -st.amount
              ELSE 0
            END
          ), 0) AS adjustment_amount
          FROM settlement_transaction st
          WHERE st.settlement_order_id=s.id AND st.status='CONFIRMED'
        ) tx ON TRUE
        WHERE 1=1
        """);
    List<Object> params = new ArrayList<>();
    if (status != null && !status.isBlank()) {
      sql.append(" AND s.status=?");
      params.add(status);
    }
    sql.append(" ORDER BY s.created_at DESC LIMIT ? OFFSET ?");
    params.add(limit);
    params.add(offset);
    return jdbc.queryForList(sql.toString(), params.toArray());
  }

  public long settlementsCount(String status) {
    StringBuilder sql = new StringBuilder("SELECT count(*) FROM settlement_order WHERE 1=1");
    List<Object> params = new ArrayList<>();
    if (status != null && !status.isBlank()) {
      sql.append(" AND status=?");
      params.add(status);
    }
    Long count = jdbc.queryForObject(sql.toString(), Long.class, params.toArray());
    return count == null ? 0 : count;
  }

  public Map<String, Object> lockSettlementForUpdate(Long settlementId) {
    return one("""
        SELECT s.id, s.settlement_no AS "settlementNo", s.amount,
               s.paid_amount AS "paidAmount", s.currency, s.status,
               CASE WHEN r.requested_service IN
                 ('ONSITE_WRITING','MEDIA_PR','DIRECT_PUBLISHING','NEWS_CONFERENCE')
                 THEN FALSE ELSE TRUE END AS "archiveOnly",
               tx.adjustment_amount AS "adjustmentAmount",
               GREATEST(s.amount + tx.adjustment_amount - s.paid_amount, 0) AS "outstandingAmount",
               tx.transaction_count AS "transactionCount"
        FROM settlement_order s
        JOIN project p ON p.id=s.project_id
        JOIN customer_requirement r ON r.id=p.requirement_id
        LEFT JOIN LATERAL (
          SELECT
            COALESCE(SUM(
              CASE
                WHEN st.transaction_type='DEBIT_ADJUSTMENT' THEN st.amount
                WHEN st.transaction_type IN ('CREDIT_ADJUSTMENT', 'WRITE_OFF') THEN -st.amount
                ELSE 0
              END
            ) FILTER (WHERE st.status='CONFIRMED'), 0) AS adjustment_amount,
            count(*) FILTER (WHERE st.status='CONFIRMED') AS transaction_count
          FROM settlement_transaction st
          WHERE st.settlement_order_id=s.id
        ) tx ON TRUE
        WHERE s.id=? FOR UPDATE OF s
        """, settlementId);
  }

  public List<Map<String, Object>> settlementTransactions(
      Long settlementId, String transactionType, String status, int limit, int offset) {
    StringBuilder sql = new StringBuilder("""
        SELECT st.id, st.transaction_no AS "transactionNo",
               st.settlement_order_id AS "settlementId", s.settlement_no AS "settlementNo",
               p.project_no AS "projectNo", p.project_name AS "projectName",
               o.name AS "organizationName", st.transaction_type AS "transactionType",
               CASE st.transaction_type
                 WHEN 'PAYMENT' THEN '收款'
                 WHEN 'REFUND' THEN '退款'
                 WHEN 'CREDIT_ADJUSTMENT' THEN '贷项调整'
                 WHEN 'DEBIT_ADJUSTMENT' THEN '借项调整'
                 WHEN 'WRITE_OFF' THEN '核销'
               END AS "transactionLabel",
               st.amount, st.currency, st.occurred_at AS "occurredAt",
               st.reference_no AS "referenceNo", st.customer_note AS "customerNote",
               st.internal_note AS "internalNote", st.status,
               creator.display_name AS "createdByName", voider.display_name AS "voidedByName",
               st.voided_at AS "voidedAt", st.void_reason AS "voidReason",
               st.created_at AS "createdAt", st.updated_at AS "updatedAt"
        FROM settlement_transaction st
        JOIN settlement_order s ON s.id=st.settlement_order_id
        JOIN project p ON p.id=s.project_id
        JOIN organization o ON o.id=s.organization_id
        JOIN app_user creator ON creator.id=st.created_by
        LEFT JOIN app_user voider ON voider.id=st.voided_by
        WHERE 1=1
        """);
    List<Object> args = new ArrayList<>();
    if (settlementId != null) {
      sql.append(" AND st.settlement_order_id=?");
      args.add(settlementId);
    }
    if (!blank(transactionType)) {
      sql.append(" AND st.transaction_type=?");
      args.add(transactionType);
    }
    if (!blank(status)) {
      sql.append(" AND st.status=?");
      args.add(status);
    }
    sql.append(" ORDER BY st.occurred_at DESC, st.id DESC LIMIT ? OFFSET ?");
    args.add(limit);
    args.add(offset);
    return jdbc.queryForList(sql.toString(), args.toArray());
  }

  public long settlementTransactionsCount(
      Long settlementId, String transactionType, String status) {
    StringBuilder sql = new StringBuilder(
        "SELECT count(*) FROM settlement_transaction st WHERE 1=1");
    List<Object> args = new ArrayList<>();
    if (settlementId != null) {
      sql.append(" AND st.settlement_order_id=?");
      args.add(settlementId);
    }
    if (!blank(transactionType)) {
      sql.append(" AND st.transaction_type=?");
      args.add(transactionType);
    }
    if (!blank(status)) {
      sql.append(" AND st.status=?");
      args.add(status);
    }
    return count(sql.toString(), args.toArray());
  }

  @Transactional
  public Map<String, Object> existingSettlementTransaction(
      Long settlementId, String submissionKey, String submissionHash) {
    Map<String, Object> transaction = one("""
        SELECT id, transaction_no AS "transactionNo",
               settlement_order_id AS "settlementId",
               transaction_type AS "transactionType", amount, currency,
               occurred_at AS "occurredAt", reference_no AS "referenceNo",
               customer_note AS "customerNote", status,
               created_at AS "createdAt", submission_hash AS "submissionHash"
        FROM settlement_transaction
        WHERE settlement_order_id=? AND submission_key=?
        """, settlementId, submissionKey);
    if (transaction.isEmpty()) return Map.of();
    if (!submissionHash.equals(String.valueOf(transaction.get("submissionHash")))) {
      throw new BusinessException(
          "IDEMPOTENCY_KEY_REUSED",
          "该请求标识已用于另一笔结算交易，请刷新页面后重试",
          HttpStatus.CONFLICT);
    }
    transaction.remove("submissionHash");
    return transaction;
  }

  @Transactional
  public Map<String, Object> createSettlementTransaction(
      AuthPrincipal user,
      Long settlementId,
      CreateSettlementTransactionRequest request,
      String submissionKey,
      String submissionHash) {
    String transactionNo = no("TRX");
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO settlement_transaction
          (transaction_no, settlement_order_id, transaction_type, amount, currency,
           occurred_at, reference_no, customer_note, internal_note, status, created_by,
           submission_key, submission_hash)
          SELECT ?, s.id, ?, ?, s.currency, ?, ?, ?, ?, 'CONFIRMED', ?, ?, ?
          FROM settlement_order s WHERE s.id=?
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, transactionNo);
      ps.setString(2, request.transactionType());
      ps.setBigDecimal(3, request.amount());
      ps.setObject(4, request.occurredAt());
      ps.setString(5, request.referenceNo());
      ps.setString(6, request.customerNote());
      ps.setString(7, request.internalNote());
      ps.setLong(8, user.userId());
      ps.setString(9, submissionKey);
      ps.setString(10, submissionHash);
      ps.setLong(11, settlementId);
      return ps;
    }, keyHolder);
    Long transactionId = key(keyHolder);
    refreshSettlementTotals(settlementId);
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("transactionNo", transactionNo);
    detail.put("transactionType", request.transactionType());
    detail.put("amount", request.amount());
    log(user, "CREATE_SETTLEMENT_TRANSACTION", "SETTLEMENT_TRANSACTION",
        String.valueOf(transactionId), detail);
    return one("""
        SELECT id, transaction_no AS "transactionNo", settlement_order_id AS "settlementId",
               transaction_type AS "transactionType", amount, currency,
               occurred_at AS "occurredAt", reference_no AS "referenceNo",
               customer_note AS "customerNote", status, created_at AS "createdAt"
        FROM settlement_transaction WHERE id=?
        """, transactionId);
  }

  public Map<String, Object> lockSettlementTransactionForUpdate(Long transactionId) {
    return one("""
        SELECT st.id, st.settlement_order_id AS "settlementId",
               st.transaction_type AS "transactionType", st.amount, st.status,
               CASE WHEN r.requested_service IN
                 ('ONSITE_WRITING','MEDIA_PR','DIRECT_PUBLISHING','NEWS_CONFERENCE')
                 THEN FALSE ELSE TRUE END AS "archiveOnly"
        FROM settlement_transaction st
        JOIN settlement_order s ON s.id=st.settlement_order_id
        JOIN project p ON p.id=s.project_id
        JOIN customer_requirement r ON r.id=p.requirement_id
        WHERE st.id=? FOR UPDATE OF st
        """, transactionId);
  }

  @Transactional
  public boolean voidSettlementTransaction(
      AuthPrincipal user, Long transactionId, Long settlementId, String reason) {
    int updated = jdbc.update("""
        UPDATE settlement_transaction
        SET status='VOIDED', voided_by=?, voided_at=CURRENT_TIMESTAMP, void_reason=?
        WHERE id=? AND settlement_order_id=? AND status='CONFIRMED'
        """, user.userId(), reason, transactionId, settlementId);
    if (updated == 0) return false;
    refreshSettlementTotals(settlementId);
    log(user, "VOID_SETTLEMENT_TRANSACTION", "SETTLEMENT_TRANSACTION",
        String.valueOf(transactionId), Map.of("reason", reason));
    return true;
  }

  private void refreshSettlementTotals(Long settlementId) {
    jdbc.update("""
        WITH totals AS (
          SELECT
            GREATEST(COALESCE(SUM(
              CASE
                WHEN transaction_type='PAYMENT' THEN amount
                WHEN transaction_type='REFUND' THEN -amount
                ELSE 0
              END
            ) FILTER (WHERE status='CONFIRMED'), 0), 0) AS net_paid,
            COALESCE(SUM(
              CASE
                WHEN transaction_type='DEBIT_ADJUSTMENT' THEN amount
                WHEN transaction_type IN ('CREDIT_ADJUSTMENT', 'WRITE_OFF') THEN -amount
                ELSE 0
              END
            ) FILTER (WHERE status='CONFIRMED'), 0) AS adjustment_amount,
            max(occurred_at) FILTER (
              WHERE status='CONFIRMED' AND transaction_type='PAYMENT'
            ) AS last_payment_at
          FROM settlement_transaction
          WHERE settlement_order_id=?
        )
        UPDATE settlement_order s
        SET paid_amount=totals.net_paid,
            paid_at=totals.last_payment_at,
            status=CASE
              WHEN s.status='PAID'
                AND GREATEST(s.amount + totals.adjustment_amount - totals.net_paid, 0) > 0
              THEN 'CONFIRMED'
              ELSE s.status
            END
        FROM totals
        WHERE s.id=?
        """, settlementId, settlementId);
  }

  public boolean updateSettlement(AuthPrincipal user, Long settlementId, String status, String invoiceNo) {
    int updated = jdbc.update("""
        UPDATE settlement_order SET status=?, invoice_no=COALESCE(?, invoice_no) WHERE id=?
        """, status, invoiceNo, settlementId);
    if (updated == 0) return false;
    log(user, "UPDATE_SETTLEMENT", "SETTLEMENT", String.valueOf(settlementId), Map.of("status", status));
    return true;
  }

  public List<Map<String, Object>> logs(int limit, int offset) {
    return jdbc.queryForList("""
        SELECT l.log_no AS "logNo", u.display_name AS "actorName", l.actor_role AS "actorRole", l.action,
               l.target_type AS "targetType", l.target_id AS "targetId", l.detail_json AS "detail", l.created_at AS "createdAt"
        FROM operation_log l LEFT JOIN app_user u ON u.id=l.actor_id
        ORDER BY l.created_at DESC LIMIT ? OFFSET ?
        """, limit, offset);
  }

  @Transactional
  public String saveFile(AuthPrincipal user, Long projectId, String originalName, String storageKey,
                       String contentType, long size, String checksum) {
    String fileNo = no("FIL");
    jdbc.update("""
        INSERT INTO file_asset
        (file_no, project_id, uploader_id, original_name, storage_key, content_type, file_size, checksum_sha256, status)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
        """, fileNo, projectId, user.userId(), originalName, storageKey, contentType, size, checksum);
    log(user, "UPLOAD_FILE", "PROJECT", String.valueOf(projectId), Map.of("fileName", originalName));
    return fileNo;
  }

  public Map<String, Object> fileAsset(String fileNo) {
    return one("""
        SELECT file_no AS "fileNo", project_id AS "projectId", storage_key AS "storageKey",
               original_name AS "originalName", content_type AS "contentType", file_size AS "fileSize"
        FROM file_asset WHERE file_no=? AND status='ACTIVE'
        """, fileNo);
  }

  public void log(AuthPrincipal user, String action, String targetType, String targetId, Map<String, ?> detail) {
    try {
      jdbc.update("""
          INSERT INTO operation_log
          (log_no, actor_id, actor_role, action, target_type, target_id, detail_json, status)
          VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, 'SUCCESS')
          """, no("LOG"), user.userId(), user.role(), action, targetType, targetId,
          objectMapper.writeValueAsString(detail == null ? Map.of() : detail));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private List<Map<String, Object>> taskRows(String where, Object... args) {
    return jdbc.queryForList("""
        SELECT t.id, t.task_no AS "taskNo", t.project_id AS "projectId", p.project_no AS "projectNo",
               p.project_name AS "projectName", t.manuscript_id AS "manuscriptId", m.title AS "manuscriptTitle",
               t.channel_type AS "channelType",
               COALESCE(mpi.media_name, c.channel_name, '媒体邀请名单待项目核验') AS "channelName",
               t.planned_publish_at AS "plannedPublishAt",
               t.actual_publish_at AS "actualPublishAt", t.execution_note AS "executionNote",
               t.exception_reason AS "exceptionReason", t.status, op.display_name AS "operatorName",
               t.updated_at AS "updatedAt", mpi.status AS "mediaInvitationStatus",
               mpi.invited_at AS "mediaInvitedAt", mpi.response_at AS "mediaRespondedAt"
        FROM publish_task t JOIN project p ON p.id=t.project_id LEFT JOIN manuscript m ON m.id=t.manuscript_id
        LEFT JOIN publish_channel c ON c.id=t.channel_id
        LEFT JOIN media_pr_invitation mpi ON mpi.publish_task_id=t.id
        LEFT JOIN app_user op ON op.id=t.assigned_operator_id
        """ + where, args);
  }

  private Map<String, Object> oneTask(String where, Object... args) {
    List<Map<String, Object>> rows = taskRows(where, args);
    return rows.isEmpty() ? Map.of() : rows.get(0);
  }

  private List<Object> projectScope(StringBuilder sql, AuthPrincipal user) {
    List<Object> args = new ArrayList<>();
    if ("CUSTOMER".equals(user.role())) { sql.append(" AND p.customer_id=?"); args.add(user.userId()); }
    if ("PUBLISH_OPERATOR".equals(user.role())) {
      sql.append("""
           AND (
             p.owner_operator_id=?
             OR EXISTS (SELECT 1 FROM publish_task pt WHERE pt.project_id=p.id AND pt.assigned_operator_id=?)
              OR EXISTS (
                SELECT 1
                FROM writing_assignment wa
                JOIN editorial_task et ON et.id=wa.editorial_task_id
                JOIN writing_assignment_member member ON member.assignment_id=wa.id
                JOIN writer_profile profile ON profile.id=member.writer_profile_id
                WHERE et.project_id=p.id AND profile.user_id=?
              )
             OR EXISTS (
               SELECT 1
               FROM conference_work_item cwi
               JOIN conference_project cp ON cp.id=cwi.conference_project_id
               WHERE cp.project_id=p.id AND cwi.assigned_operator_id=?
             )
           )
          """);
      args.add(user.userId());
      args.add(user.userId());
      args.add(user.userId());
      args.add(user.userId());
    }
    return args;
  }

  private List<Object> taskScope(StringBuilder sql, AuthPrincipal user) {
    List<Object> args = new ArrayList<>();
    if ("CUSTOMER".equals(user.role())) { sql.append(" AND p.customer_id=?"); args.add(user.userId()); }
    if ("PUBLISH_OPERATOR".equals(user.role())) { sql.append(" AND t.assigned_operator_id=?"); args.add(user.userId()); }
    return args;
  }

  private Map<String, Object> one(String sql, Object... args) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
    return rows.isEmpty() ? Map.of() : rows.get(0);
  }

  private long number(Object value) {
    if (value instanceof Number number) return number.longValue();
    return Long.parseLong(String.valueOf(value));
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Unable to snapshot media candidate", ex);
    }
  }

  private long count(String sql, Object... args) {
    Long count = jdbc.queryForObject(sql, Long.class, args);
    return count == null ? 0 : count;
  }

  private Long key(KeyHolder keyHolder) {
    if (keyHolder.getKeys() != null && keyHolder.getKeys().get("id") instanceof Number number) return number.longValue();
    if (keyHolder.getKey() != null) return keyHolder.getKey().longValue();
    throw new IllegalStateException("Generated key missing");
  }

  private OffsetDateTime conferenceWorkItemDueAt(OffsetDateTime eventTime, int sortOrder) {
    if (eventTime == null) return null;
    return switch (sortOrder) {
      case 1 -> eventTime.minusDays(30);
      case 2 -> eventTime.minusDays(21);
      case 3 -> eventTime.minusDays(14);
      case 4 -> eventTime.minusDays(10);
      case 5 -> eventTime.minusDays(7);
      case 6 -> eventTime.minusDays(3);
      case 7 -> eventTime;
      case 8 -> eventTime.plusDays(1);
      case 9 -> eventTime.plusDays(7);
      default -> null;
    };
  }

  private String conferenceWorkItemPhase(int sortOrder) {
    if (sortOrder <= 6) return "PRE_EVENT";
    if (sortOrder == 7) return "ONSITE";
    return "POST_EVENT";
  }

  private String supplierOrderHistoryNote(
      Map<String, Object> previous, UpdateSupplierOrderRequest request) {
    List<String> parts = new ArrayList<>();
    if (!blank(request.note())) parts.add(request.note().trim());

    String currentTrace = supplierFulfillmentTrace(
        request.fulfillmentMode(), request.externalOrderNo(), request.submissionEvidenceReference());
    String previousTrace = supplierFulfillmentTrace(
        valueString(previous.get("fulfillmentMode")),
        valueString(previous.get("externalOrderNo")),
        valueString(previous.get("submissionEvidenceReference")));
    if ("PENDING_SUBMISSION".equals(request.status())
        && !"PENDING_SUBMISSION".equals(valueString(previous.get("status")))) {
      parts.add("重新进入待提交，当前履约信息已清除");
      if (!blank(previousTrace)) parts.add("此前" + previousTrace);
    } else if (!blank(currentTrace)) {
      parts.add(currentTrace);
    } else if (!blank(previousTrace)
        && ("EXCEPTION".equals(request.status()) || "CANCELLED".equals(request.status()))) {
      parts.add("此前" + previousTrace);
    }
    return parts.isEmpty() ? null : String.join("；", parts);
  }

  private String supplierFulfillmentTrace(
      String fulfillmentMode, String externalOrderNo, String evidenceReference) {
    if (blank(evidenceReference) || "UNCONFIRMED".equals(fulfillmentMode)) return null;
    String mode = "API".equals(fulfillmentMode) ? "接口回执" : "人工提交凭据";
    StringBuilder trace = new StringBuilder("履约方式：").append(mode)
        .append("；证据：").append(evidenceReference.trim());
    if ("API".equals(fulfillmentMode) && !blank(externalOrderNo)) {
      trace.append("；上游订单号：").append(externalOrderNo.trim());
    }
    return trace.toString();
  }

  private String valueString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private String blankToNull(String value) {
    return blank(value) ? null : value;
  }

  private String defaultIfBlank(String value, String fallback) {
    return blank(value) ? fallback : value;
  }

  public static String no(String prefix) {
    return prefix + "-" + java.time.LocalDate.now().toString().replace("-", "") + "-" +
        UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
  }
}
