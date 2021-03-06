package ut.org.catrobat.jira.timesheet.rest;

import com.atlassian.activeobjects.test.TestActiveObjects;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.user.util.UserUtil;
import com.atlassian.mail.queue.MailQueue;
import net.java.ao.EntityManager;
import net.java.ao.test.jdbc.Data;
import net.java.ao.test.junit.ActiveObjectsJUnitRunner;
import org.catrobat.jira.timesheet.activeobjects.*;
import org.catrobat.jira.timesheet.services.impl.ConfigServiceImpl;
import org.catrobat.jira.timesheet.rest.SchedulingRest;
import org.catrobat.jira.timesheet.scheduling.TimesheetScheduler;
import org.catrobat.jira.timesheet.services.*;
import org.catrobat.jira.timesheet.services.impl.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import ut.org.catrobat.jira.timesheet.activeobjects.MySampleDatabaseUpdater;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(ActiveObjectsJUnitRunner.class)
@Data(MySampleDatabaseUpdater.class)
@PrepareForTest({ComponentAccessor.class, SchedulingRest.class})
public class SchedulingRestTest {

    private SchedulingRest schedulingRestMock;
    private UserManager userManagerJiraMock;
    private ConfigService configServiceMock;
    private PermissionService permissionServiceMock;
    private TimesheetEntryService timesheetEntryServiceMock;
    private TimesheetService timesheetServiceMock;
    private TeamService teamServiceMock;
    private UserUtil userUtilMock;
    private MailQueue mailQueueMock;
    private HttpServletRequest httpRequest;
    private TestActiveObjects ao;
    private EntityManager entityManager;
    private CategoryServiceImpl categoryService;
    private ConfigServiceImpl configService;
    private TeamServiceImpl teamService;
    private TimesheetEntryServiceImpl timesheetEntryService;
    private TimesheetServiceImpl timesheetService;
    private SchedulingRest schedulingRest;
    private TimesheetScheduler timesheetScheduler;
    private SchedulingService schedulingService;

    @Before
    public void setUp() throws Exception {
        assertNotNull(entityManager);
        ao = new TestActiveObjects(entityManager);

        configServiceMock = mock(ConfigService.class, RETURNS_DEEP_STUBS);
        permissionServiceMock = mock(PermissionService.class, RETURNS_DEEP_STUBS);
        timesheetEntryServiceMock = mock(TimesheetEntryService.class, RETURNS_DEEP_STUBS);
        timesheetServiceMock = mock(TimesheetService.class, RETURNS_DEEP_STUBS);
        teamServiceMock = mock(TeamService.class, RETURNS_DEEP_STUBS);
        userManagerJiraMock = mock(UserManager.class, RETURNS_DEEP_STUBS);
        userUtilMock = mock(UserUtil.class, RETURNS_DEEP_STUBS);
        mailQueueMock = mock(MailQueue.class, RETURNS_DEEP_STUBS);
        httpRequest = mock(HttpServletRequest.class, RETURNS_DEEP_STUBS);
        timesheetScheduler = mock(TimesheetScheduler.class, RETURNS_DEEP_STUBS);
        schedulingService = mock(SchedulingService.class, RETURNS_DEEP_STUBS);

        categoryService = new CategoryServiceImpl(ao);
        timesheetService = new TimesheetServiceImpl(ao);
        timesheetEntryService = new TimesheetEntryServiceImpl(ao, timesheetService);
        teamService = new TeamServiceImpl(ao, timesheetEntryService);
        configService = new ConfigServiceImpl(ao, categoryService, teamService);

        // For some tests we need a mock...
        schedulingRestMock = new SchedulingRest(configServiceMock, permissionServiceMock, timesheetEntryServiceMock,
                timesheetServiceMock, teamServiceMock, categoryService, timesheetScheduler, schedulingService);

        // ... and for some tests we need a real instance of the class
        schedulingRest = new SchedulingRest(configService, permissionServiceMock, timesheetEntryService,
                timesheetService, teamService, categoryService, timesheetScheduler, schedulingService);

        // ... and sometimes you would like to mix them together (see in test method)

        // info: mock static method
        PowerMockito.mockStatic(ComponentAccessor.class);
        PowerMockito.when(ComponentAccessor.getUserManager()).thenReturn(userManagerJiraMock);
        PowerMockito.when(ComponentAccessor.getUserUtil()).thenReturn(userUtilMock);
        PowerMockito.when(ComponentAccessor.getMailQueue()).thenReturn(mailQueueMock);
    }

    @Test
    public void testActivityNotification_unauthorized() throws Exception {
        //preparations
        when(permissionServiceMock.checkUserPermission()).thenReturn(mock(Response.class));

        //execution & verifying
        schedulingRestMock.activityNotification(httpRequest);
        Mockito.verify(timesheetServiceMock, never()).all();
    }

    @Test
    public void testActivityNotification_TimesheetEntryIsEmpty() throws Exception {
        timesheetService.add("key 1", "user 1", 450, 450, 900, 200, 0, "master thesis", "", true, true, Timesheet.State.ACTIVE); // master thesis
        timesheetService.add("key 2", "user 2", 450, 0, 450, 450, 0, "bachelor thesis", "", false, false, Timesheet.State.ACTIVE); // disabled
        timesheetService.add("key 3", "user 3", 450, 0, 450, 200, 20, "seminar paper", "", false, true, Timesheet.State.INACTIVE); // inactive

        ApplicationUser user1 = mock(ApplicationUser.class);
        ApplicationUser user2 = mock(ApplicationUser.class);

        when(user1.getName()).thenReturn("MarkusHobisch");
        when(user2.getName()).thenReturn("AdrianSchnedlitz");
        when(ComponentAccessor.getUserManager().getUserByName(user1.getName()).getKey()).thenReturn("key 1");
        when(ComponentAccessor.getUserManager().getUserByName(user2.getName()).getKey()).thenReturn("key 2");

        Config config = mock(Config.class);
        when(configServiceMock.getConfiguration()).thenReturn(config);

        when(permissionServiceMock.checkUserPermission()).thenReturn(null);

        schedulingRest.activityNotification(httpRequest);
    }

    @Test
    public void testActivityNotification_differentKindsOfTimesheets() throws Exception {
        Timesheet timesheet1 = timesheetService.add("key 1", "user 1", 450, 450, 900, 200, 0, "master thesis", "", true, true, Timesheet.State.ACTIVE);

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        Date yesterday = cal.getTime();

        Date today = new Date();

        cal.add(Calendar.DATE, +1);

        Category categoryDrone = categoryService.add("Drone");
        Team droneTeam = teamService.add("Drone Team");

        ApplicationUser user1 = mock(ApplicationUser.class);
        ApplicationUser user2 = mock(ApplicationUser.class);

        when(user1.getName()).thenReturn("MarkusHobisch");
        when(user2.getName()).thenReturn("AdrianSchnedlitz");
        when(ComponentAccessor.getUserManager().getUserByName(user1.getName()).getKey()).thenReturn("key 1");
        when(ComponentAccessor.getUserManager().getUserByName(user2.getName()).getKey()).thenReturn("key 2");

        Config config = mock(Config.class);
        when(configServiceMock.getConfiguration()).thenReturn(config);

        when(permissionServiceMock.checkUserPermission()).thenReturn(null);

        timesheetEntryService.add(timesheet1, yesterday, today, categoryDrone, "testing a lot of things",
                30, droneTeam, false, today, today, "123456", "MarkusHobisch"); // this should work

        // info: mock private method
        SchedulingRest spy = PowerMockito.spy(schedulingRest);

        // execute your test
        spy.activityNotification(httpRequest);

        // verify your calls
        //PowerMockito.verifyPrivate(spy, never()).invoke("sendMail", Matchers.anyObject());

        timesheetService.remove(timesheet1);
        Timesheet timesheet2 = timesheetService.add("key 1", "user 1", 450, 450, 900, 200, 0, "master thesis", "", true, true, Timesheet.State.INACTIVE); // inactive

        timesheetEntryService.add(timesheet2, yesterday, today, categoryDrone, "testing a lot of things",
                30, droneTeam, false, today, today, "123456", "MarkusHobisch"); // this should work

        // execute your test
        spy.activityNotification(httpRequest);

        // verify your calls
        //PowerMockito.verifyPrivate(spy, never()).invoke("sendMail", Matchers.anyObject());
    }
}
