package io.metersphere.track.controller;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.metersphere.base.domain.TestCaseReviewTestCase;
import io.metersphere.commons.constants.OperLogConstants;
import io.metersphere.commons.constants.OperLogModule;
import io.metersphere.commons.constants.PermissionConstants;
import io.metersphere.commons.utils.PageUtils;
import io.metersphere.commons.utils.Pager;
import io.metersphere.controller.request.ResetOrderRequest;
import io.metersphere.log.annotation.MsAuditLog;
import io.metersphere.track.dto.TestReviewCaseDTO;
import io.metersphere.track.request.testplancase.TestReviewCaseBatchRequest;
import io.metersphere.track.request.testreview.DeleteRelevanceRequest;
import io.metersphere.track.request.testreview.QueryCaseReviewRequest;
import io.metersphere.track.service.TestReviewTestCaseService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RequestMapping("/test/review/case")
@RestController
public class TestReviewTestCaseController {

    @Resource
    TestReviewTestCaseService testReviewTestCaseService;

    @PostMapping("/list/{goPage}/{pageSize}")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_REVIEW_READ)
    public Pager<List<TestReviewCaseDTO>> getTestPlanCases(@PathVariable int goPage, @PathVariable int pageSize, @RequestBody QueryCaseReviewRequest request) {
        Page<Object> page = PageHelper.startPage(goPage, pageSize, true);
        return PageUtils.setPageInfo(page, testReviewTestCaseService.list(request));
    }

    @PostMapping("/delete")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_REVIEW_READ_RELEVANCE_OR_CANCEL)
    @MsAuditLog(module = OperLogModule.TRACK_TEST_CASE_REVIEW, type = OperLogConstants.UN_ASSOCIATE_CASE, beforeEvent = "#msClass.getLogDetails(#request)", msClass = TestReviewTestCaseService.class)
    public int deleteTestCase(@RequestBody DeleteRelevanceRequest request) {
        return testReviewTestCaseService.deleteTestCase(request);
    }

    @PostMapping("/batch/delete")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_REVIEW_READ_RELEVANCE_OR_CANCEL)
    @MsAuditLog(module = OperLogModule.TRACK_TEST_CASE_REVIEW, type = OperLogConstants.UN_ASSOCIATE_CASE, beforeEvent = "#msClass.getLogDetails(#request)", msClass = TestReviewTestCaseService.class)
    public void deleteTestCaseBatch(@RequestBody TestReviewCaseBatchRequest request) {
        testReviewTestCaseService.deleteTestCaseBatch(request);
    }

    @PostMapping("/batch/edit/status")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_REVIEW_READ_REVIEW)
    @MsAuditLog(module = OperLogModule.TRACK_TEST_CASE_REVIEW, type = OperLogConstants.BATCH_UPDATE, beforeEvent = "#msClass.batchLogDetails(#request)", content = "#msClass.getLogDetails(#request)", msClass = TestReviewTestCaseService.class)
    public void editTestCaseBatch(@RequestBody TestReviewCaseBatchRequest request) {
        testReviewTestCaseService.editTestCaseBatchStatus(request);
    }

    @PostMapping("/minder/edit/{reviewId}")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_REVIEW_READ_REVIEW)
    @MsAuditLog(module = OperLogModule.TRACK_TEST_CASE_REVIEW, type = OperLogConstants.ASSOCIATE_CASE, content = "#msClass.getLogDetails(#testCases)", msClass = TestReviewTestCaseService.class)
    public void editTestCaseForMinder(@PathVariable("reviewId") String reviewId, @RequestBody List<TestCaseReviewTestCase> testCases) {
        testReviewTestCaseService.editTestCaseForMinder(reviewId, testCases);
    }

    @PostMapping("/list/minder")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_REVIEW_READ)
    public List<TestReviewCaseDTO> listForMinder(@RequestBody QueryCaseReviewRequest request) {
        return testReviewTestCaseService.listForMinder(request);
    }

    @PostMapping("/list/minder/{goPage}/{pageSize}")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_REVIEW_READ)
    public Pager<List<TestReviewCaseDTO>> listForMinder(@PathVariable int goPage, @PathVariable int pageSize, @RequestBody QueryCaseReviewRequest request) {
        Page<Object> page = PageHelper.startPage(goPage, pageSize, true);
        return PageUtils.setPageInfo(page, testReviewTestCaseService.listForMinder(request));
    }

    @PostMapping("/edit")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_REVIEW_READ_REVIEW)
    @MsAuditLog(module = OperLogModule.TRACK_TEST_CASE_REVIEW, type = OperLogConstants.REVIEW, content = "#msClass.getLogDetails(#testCaseReviewTestCase)", msClass = TestReviewTestCaseService.class)
    public void editTestCase(@RequestBody TestCaseReviewTestCase testCaseReviewTestCase) {
        testReviewTestCaseService.editTestCase(testCaseReviewTestCase);
    }

    @GetMapping("/get/{reviewId}")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_REVIEW_READ)
    public TestReviewCaseDTO get(@PathVariable String reviewId) {
        return testReviewTestCaseService.get(reviewId);
    }

    @PostMapping("/edit/order")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_REVIEW_READ_RELEVANCE_OR_CANCEL)
    public void orderCase(@RequestBody ResetOrderRequest request) {
        testReviewTestCaseService.updateOrder(request);
    }

}
