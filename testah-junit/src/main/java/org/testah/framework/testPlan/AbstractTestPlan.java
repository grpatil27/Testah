package org.testah.framework.testPlan;

import java.lang.reflect.Method;
import java.util.HashMap;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Test.None;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.testah.TS;
import org.testah.client.dto.StepActionDto;
import org.testah.client.dto.TestCaseDto;
import org.testah.client.dto.TestPlanDto;
import org.testah.client.dto.TestStepDto;
import org.testah.framework.annotations.KnownProblem;
import org.testah.framework.annotations.TestCase;
import org.testah.framework.annotations.TestPlan;
import org.testah.framework.cli.Cli;
import org.testah.framework.cli.TestFilter;
import org.testah.framework.dto.StepAction;
import org.testah.framework.dto.TestDtoHelper;
import org.testah.framework.report.TestPlanReporter;
import org.testah.runner.testPlan.TestPlanActor;


/**
 * The Class AbstractTestPlan.
 */
@ContextHierarchy({ @ContextConfiguration(classes = TestConfiguration.class) })
public abstract class AbstractTestPlan extends AbstractJUnit4SpringContextTests {

    /** The test plan. */
    private static ThreadLocal<TestPlanDto>  testPlan;
    
    /** The test case. */
    private static ThreadLocal<TestCaseDto>  testCase;
    
    /** The test step. */
    private static ThreadLocal<TestStepDto>  testStep;
    
    /** The test plan start. */
    private static ThreadLocal<Boolean>      testPlanStart = new ThreadLocal<Boolean>();
    
    /** The test filter. */
    private static TestFilter                testFilter    = null;
    
    /** The ignored tests. */
    private static ThreadLocal<HashMap<String,String>> ignoredTests  = null;

    /** The name. */
    public TestName                          name          = new TestName();

    /** The global timeout. */
    public TestRule                          globalTimeout = Timeout.millis(100000L);

    /** The initialize. */
    public ExternalResource                  initialize    = new ExternalResource() {

                                                               protected void before() throws Throwable {
                                                                   initlizeTest();
                                                               }

                                                               protected void after() {
                                                                   tearDownTest();
                                                               };
                                                           };

    /**
     * Initlize test.
     */
    public abstract void initlizeTest();

    /**
     * Tear down test.
     */
    public abstract void tearDownTest();

    /** The filter. */
    public TestWatcher filter    = new TestWatcher() {

                                     public Statement apply(final Statement base, final Description description) {
                                         final String name = description.getClassName() + "#"
                                                 + description.getMethodName();

                                         
                                         /*
                                         final String onlyRun = System.getProperty("only_run");
                                         Assume.assumeTrue(onlyRun == null || Arrays.asList(onlyRun.split(","))
                                                 .contains(description.getTestClass().getSimpleName()));
                                         final String mth = System.getProperty("method");
                                         Assume.assumeTrue(mth == null || Arrays.asList(mth.split(","))
                                                 .contains(description.getMethodName()));
                                          */
                                         
                                         try{
                                         if (!getTestFilter().filterTestCase(description.getAnnotation(TestCase.class),
                                                 name)) {
                                             addIgnoredTest(name,"METADATA_FILTER");
                                             Assume.assumeTrue("Filtered out, For details use Trace level logging",
                                                     false);
                                         }
                                         }catch(Exception e){
                                             TS.log().warn("Unable to run filtering, groovy must be loaded in the project", e);
                                         }

                                         KnownProblem kp = description.getAnnotation(KnownProblem.class);
                                         if (null != kp) {
                                             if (TS.params().getFilterIgnoreKnownProblem()) {
                                                 addIgnoredTest(name,"KNOWN_PROBLEM_FILTER");
                                                 Assume.assumeTrue(
                                                         "Filtered out, KnownProblem found: " + kp.description(),
                                                         false);
                                             }
                                         }

                                         return super.apply(base, description);

                                     }
                                 };

    /** The watchman2. */
    public TestWatcher watchman2 = new TestWatcher() {

                                     protected void failed(final Throwable e, final Description description) {
                                         StepAction.createAssertResult("Unexpected Error occured", false,
                                                 "UnhandledExceptionFoundByJUnit", "", e.getMessage(), e).add();

                                         TS.log().error("TESTCASE Status: failed", e);
                                         stopTestCase(false);
                                     }

                                     protected void succeeded(final Description description) {

                                         stopTestCase(null);
                                         TS.log().info("TESTCASE Status: " + getTestCase().getStatusEnum());
                                         try {
                                             final Test testAnnotation = description.getAnnotation(Test.class);
                                             if (null != testAnnotation && None.class == testAnnotation.expected()) {
                                                 if (null != getTestCase()) {

                                                     getTestCase().getAssertionError();
                                                 }
                                             }
                                         } catch (final AssertionError ae) {
                                             TS.log().error("Exception Thrown Looking at TestCase Assert History\n"
                                                     + ae.getMessage());
                                             throw ae;
                                         }

                                     }

                                     protected void finished(final Description desc) {
                                         TS.log().info("TESTCASE Complete: " + desc.getDisplayName() + " - thread["
                                                 + Thread.currentThread().getId() + "]");
                                     }

                                     protected void starting(final Description desc) {

                                         if (!didTestPlanStart()) {
                                             TS.log().info("TESTPLAN started:" + desc.getTestClass().getName()
                                                     + " - thread[" + Thread.currentThread().getId() + "]");
                                             startTestPlan(desc, desc.getTestClass().getAnnotation(TestPlan.class),
                                                     desc.getTestClass().getAnnotation(KnownProblem.class));
                                             getTestPlan().setRunInfo(TestDtoHelper.createRunInfo());
                                             
                                             for(Method m : desc.getTestClass().getDeclaredMethods()){
                                                 if(null!=m.getAnnotation(Ignore.class)){
                                                     addIgnoredTest(desc.getClassName() + "#" + m.getName(),"JUNIT_IGNORE");
                                                 }
                                             }
                                             
                                         }
                                         TS.log().info(
                                                  Cli.BAR_LONG);
                                         TS.log().info("TESTCASE started:" + desc.getDisplayName() + " - thread["
                                                 + Thread.currentThread().getId() + "]");
                                         startTestCase(desc, desc.getAnnotation(TestCase.class),
                                                 desc.getTestClass().getAnnotation(TestPlan.class),
                                                 desc.getTestClass().getAnnotation(KnownProblem.class));
                                     }
                                 };

    /** The chain. */
    @Rule
    public TestRule    chain     = RuleChain.outerRule(watchman2).around(initialize).around(name).around(filter);

    /**
     * Setup abstract test plan.
     */
    @BeforeClass
    public static void setupAbstractTestPlan() {
        try {

        } catch (final Exception e) {

        }
    }

    /**
     * Tear down abstract test plan.
     */
    @AfterClass
    public static void tearDownAbstractTestPlan() {
        try {
            if (TS.isBrowser()) {

                if (!TestPlanActor.isResultsInUse()) {
                    TS.browser().close();
                }

            }
            if (null != getTestPlan()) {
                getTestPlan().stop();
            }
            if (!TestPlanActor.isResultsInUse()) {
                TestPlanReporter.reportResults(getTestPlan());
            }

        } catch (final Exception e) {
            TS.log().error("after testplan", e);
        }
    }

    /**
     * Do on fail.
     */
    public abstract void doOnFail();

    /**
     * Do on pass.
     */
    public abstract void doOnPass();

    /**
     * Did test plan start.
     *
     * @return true, if successful
     */
    private static boolean didTestPlanStart() {
        if (null == testPlanStart.get()) {
            testPlanStart.set(false);
        }
        return testPlanStart.get();
    }

    /**
     * Sets the test plan start.
     *
     * @param testPlanStart the new test plan start
     */
    private static void setTestPlanStart(final boolean testPlanStart) {
        AbstractTestPlan.testPlanStart.set(testPlanStart);
    }

    /**
     * Gets the test plan thread local.
     *
     * @return the test plan thread local
     */
    private static ThreadLocal<TestPlanDto> getTestPlanThreadLocal() {
        if (null == testPlan) {
            testPlan = new ThreadLocal<TestPlanDto>();
        }
        return testPlan;
    }

    /**
     * Gets the test plan.
     *
     * @return the test plan
     */
    public static TestPlanDto getTestPlan() {
        return getTestPlanThreadLocal().get();
    }

    /**
     * Gets the test case thread local.
     *
     * @return the test case thread local
     */
    private static ThreadLocal<TestCaseDto> getTestCaseThreadLocal() {
        if (null == testCase) {
            testCase = new ThreadLocal<TestCaseDto>();
        }
        return testCase;
    }

    /**
     * Gets the test case.
     *
     * @return the test case
     */
    private static TestCaseDto getTestCase() {
        return getTestCaseThreadLocal().get();
    }

    /**
     * Gets the test step.
     *
     * @return the test step
     */
    public static TestStepDto getTestStep() {
        if (null == testStep) {
            testStep = new ThreadLocal<TestStepDto>();
        }
        if (null == testStep.get() && null != getTestCase()) {
            AbstractTestPlan.testStep.set(new TestStepDto("Initial Step", "").start());
            TS.log().info("TESTSTEP starting - " + AbstractTestPlan.testStep.get().getName());
        }
        return testStep.get();
    }

    /**
     * Gets the test step thread local.
     *
     * @return the test step thread local
     */
    public static ThreadLocal<TestStepDto> getTestStepThreadLocal() {
        if (null == testStep) {
            testStep = new ThreadLocal<TestStepDto>();
        }
        return testStep;
    }

    /**
     * Start test plan.
     *
     * @param desc the desc
     * @param testPlan the test plan
     * @param knowProblem the know problem
     * @return the test plan dto
     */
    private TestPlanDto startTestPlan(final Description desc, final TestPlan testPlan, final KnownProblem knowProblem) {
        getTestPlanThreadLocal().set(TestDtoHelper.createTestPlanDto(desc, testPlan, knowProblem).start());
        setTestPlanStart(true);
        return AbstractTestPlan.testPlan.get();
    }

    /**
     * Stop test plan.
     */
    public static void stopTestPlan() {
        setTestPlanStart(false);
    }

    /**
     * Start test case.
     *
     * @param desc the desc
     * @param testCase the test case
     * @param testPlan the test plan
     * @param knowProblem the know problem
     * @return the test case dto
     */
    private TestCaseDto startTestCase(final Description desc, final TestCase testCase, final TestPlan testPlan,
            final KnownProblem knowProblem) {
        if (didTestPlanStart()) {
            getTestCaseThreadLocal()
                    .set(TestDtoHelper.createTestCaseDto(desc, testCase, knowProblem, testPlan).start());
        }
        return getTestCase();
    }

    /**
     * Stop test case.
     *
     * @param status the status
     * @return the boolean
     */
    private static Boolean stopTestCase(final Boolean status) {
        if (null != getTestCase()) {
            stopTestStep();
            getTestPlan().addTestCase(getTestCase().stop(status));
            return getTestCase().getStatus();
        }
        return null;
    }

    /**
     * Start test step.
     *
     * @param testStep the test step
     * @return the test step dto
     */
    public static TestStepDto startTestStep(final TestStepDto testStep) {
        if (didTestPlanStart() && null != getTestCase()) {
            stopTestStep();
            getTestStepThreadLocal().set(testStep.start());
        }
        return getTestStep();
    }

    /**
     * Stop test step.
     */
    private static void stopTestStep() {
        if (null != getTestStep()) {
            getTestCase().addTestStep(getTestStep().stop());
            testStep = null;
        }
    }

    /**
     * Adds the step action.
     *
     * @param stepAction the step action
     * @return true, if successful
     */
    public static boolean addStepAction(final StepActionDto stepAction) {
        if (null == getTestStep()) {
            return false;
        }
        getTestStep().addStepAction(stepAction);
        return true;
    }

    /**
     * Step.
     *
     * @return the test step dto
     */
    public TestStepDto step() {
        return step("Step");
    }

    /**
     * Step.
     *
     * @param name the name
     * @return the test step dto
     */
    public TestStepDto step(final String name) {
        TestStepDto s = new TestStepDto();
        s.setName(name);
        return startTestStep(s);
    }

    /**
     * Step.
     *
     * @param name the name
     * @param description the description
     * @return the test step dto
     */
    public TestStepDto step(final String name, final String description) {
        TestStepDto s = new TestStepDto();
        s.setName(name);
        s.setDescription(description);
        return startTestStep(s);
    }

    /**
     * Step action.
     *
     * @return the step action dto
     */
    public StepActionDto stepAction() {
        return new StepActionDto();
    }

    /**
     * Step action info.
     *
     * @param message1 the message1
     * @return the step action dto
     */
    public StepActionDto stepActionInfo(final String message1) {
        return StepAction.createInfo(message1);
    }

    /**
     * Data value.
     *
     * @param value the value
     */
    public void dataValue(final String value) {
        if (null == value) {
            getTestCase().setDataValue("");
        } else if (value.length() > 255) {
            TS.log().debug("Data Value can only be 255 chars, truncating value");
            getTestCase().setDataValue(value.substring(0, 254));
        } else {
            getTestCase().setDataValue(value);
        }
    }

    /**
     * Gets the test filter.
     *
     * @return the test filter
     */
    public static TestFilter getTestFilter() {
        if (null == testFilter) {
            testFilter = new TestFilter();
        }
        return testFilter;
    }

    /**
     * Sets the test filter.
     *
     * @param testFilter the new test filter
     */
    public static void setTestFilter(TestFilter testFilter) {
        AbstractTestPlan.testFilter = testFilter;
    }

    /**
     * Gets the ignored tests.
     *
     * @return the ignored tests
     */
    public static HashMap<String, String> getIgnoredTests() {
        if (null == ignoredTests) {
            ignoredTests = new ThreadLocal<HashMap<String,String >>();
            ignoredTests.set(new HashMap<String, String>());
        }
        return ignoredTests.get();
    }

    /**
     * Adds the ignored test.
     *
     * @param testCaseName the test case name
     * @param reason the reason
     */
    public static void addIgnoredTest(final String testCaseName,final String reason) {
        getIgnoredTests().put(testCaseName, reason);
    }

}
