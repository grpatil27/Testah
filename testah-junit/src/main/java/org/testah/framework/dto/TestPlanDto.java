package org.testah.framework.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.junit.runner.Description;
import org.testah.framework.annotations.TestMeta;
import org.testah.framework.enums.TestStatus;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class TestPlanDto {
	private TestMetaDto meta = new TestMetaDto();
	private RunTimeDto runTime = new RunTimeDto();
	private List<TestCaseDto> testCases = new ArrayList<TestCaseDto>();
	private Boolean status = null;
	private final RunInfoDto runInfo = new RunInfoDto();

	public TestPlanDto() {
		this(null);
	}

	public TestPlanDto(final TestMeta meta) {
		this.meta.fillFromTestMeta(meta);
	}

	public TestPlanDto(final Description desc, final TestMeta meta) {
		this.meta.setName(desc.getClassName());
		this.meta.setSource(desc.getTestClass().getCanonicalName());
		if (null != meta) {
			this.meta.fillFromTestMeta(meta);
		}
	}

	public TestPlanDto addTestCase(final TestCaseDto testCase) {
		if (null != testCase) {
			getTestCases().add(testCase);
		}
		return this;
	}

	public TestPlanDto start() {
		setStatus(null);
		getRunTime().start();
		return this;
	}

	public TestPlanDto stop() {
		setStatus();
		getRunTime().stop();
		runInfo.recalc(this);
		return this;
	}

	public RunInfoDto getRunInfo() {
		return runInfo;
	}

	public TestPlanDto setStatus() {
		for (final TestCaseDto e : testCases) {
			if (null == e.getStatus()) {

			} else if (e.getStatus() == false) {
				status = false;
				return this;
			} else if (e.getStatus() == true) {
				status = true;
			}
		}
		return this;
	}

	public TestMetaDto getMeta() {
		return meta;
	}

	public void setMeta(final TestMetaDto meta) {
		this.meta = meta;
	}

	public RunTimeDto getRunTime() {
		return runTime;
	}

	public void setRunTime(final RunTimeDto runTime) {
		this.runTime = runTime;
	}

	public Boolean getStatus() {
		return status;
	}

	public void setStatus(final Boolean status) {
		this.status = status;
	}

	public List<TestCaseDto> getTestCases() {
		return testCases;
	}

	public void setTestCases(final List<TestCaseDto> testCases) {
		this.testCases = testCases;
	}

	public TestStatus getStatusEnum() {
		return TestStatus.getStatus(status);
	}

}
