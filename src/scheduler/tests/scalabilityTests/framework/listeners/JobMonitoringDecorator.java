/*
 * ################################################################
 *
 * ProActive: The Java(TM) library for Parallel, Distributed,
 *            Concurrent computing with Security and Mobility
 *
 * Copyright (C) 1997-2010 INRIA/University of
 * 				Nice-Sophia Antipolis/ActiveEon
 * Contact: proactive@ow2.org or contact@activeeon.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 3 of
 * the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 * If needed, contact us to obtain a release under GPL Version 2
 * or a different license than the GPL.
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://proactive.inria.fr/team_members.htm
 *  Contributor(s):
 *
 * ################################################################
 * $PROACTIVE_INITIAL_DEV$
 */
package scalabilityTests.framework.listeners;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.proactive.extensions.annotation.RemoteObject;
import org.ow2.proactive.scheduler.common.NotificationData;
import org.ow2.proactive.scheduler.common.SchedulerEvent;
import org.ow2.proactive.scheduler.common.SchedulerEventListener;
import org.ow2.proactive.scheduler.common.job.JobId;
import org.ow2.proactive.scheduler.common.job.JobInfo;
import org.ow2.proactive.scheduler.common.job.JobState;
import org.ow2.proactive.scheduler.common.job.UserIdentification;
import org.ow2.proactive.scheduler.common.task.TaskInfo;


/**
 * A decorator for {@link SchedulerEventListener}s, which provides
 * 	mechanisms for an outside entity to wait until a job is finished.
 *
 *  Callers can use the jobFinished method to see if a job is finished(nonblocking call)
 *  Callers can use the waitJobFinished to blocking wait until the job is finished
 *
 * @author fabratu
 *
 */
@RemoteObject
public abstract class JobMonitoringDecorator implements SchedulerEventListener {

    private SchedulerEventListener decorated;
    // monitored jobs
    protected Map<JobId, ConnectorJobInfo> mapOfJobs = Collections
            .synchronizedMap(new HashMap<JobId, ConnectorJobInfo>());

    private class ConnectorJobInfo {
        boolean jobFinished;
        final Object lock;

        public ConnectorJobInfo() {
            jobFinished = false;
            lock = new Object();
        }
    }

    public JobMonitoringDecorator() {
    }

    public JobMonitoringDecorator(SchedulerEventListener listener) {
        this.decorated = listener;
    }

    public void jobSubmittedEvent(JobState jobInfo) {
        this.decorated.jobSubmittedEvent(jobInfo);
    }

    // decorated interface delegation
    public void schedulerStateUpdatedEvent(SchedulerEvent eventType) {
        this.decorated.schedulerStateUpdatedEvent(eventType);
    }

    public void taskStateUpdatedEvent(NotificationData<TaskInfo> taskNotification) {
        this.decorated.taskStateUpdatedEvent(taskNotification);
    }

    public void usersUpdatedEvent(NotificationData<UserIdentification> notification) {
        this.decorated.usersUpdatedEvent(notification);
    }

    // this is a bit different
    public void jobStateUpdatedEvent(NotificationData<JobInfo> jobNotification) {
        // take care of this first; decorator's impl could do freaky shiet like call system.exit!
        if (jobNotification.getEventType().equals(SchedulerEvent.JOB_RUNNING_TO_FINISHED) &&
            this.mapOfJobs.containsKey(jobNotification.getData().getJobId())) {
            jobRunningToFinishedEvent(jobNotification);
        }
        decorated.jobStateUpdatedEvent(jobNotification);
    }

    protected void jobRunningToFinishedEvent(NotificationData<JobInfo> event) {
        ConnectorJobInfo job = mapOfJobs.get(event.getData().getJobId());
        // are we monitoring this job?
        if (job == null)
            return;

        synchronized (job.lock) {
            job.jobFinished = true;
            job.lock.notifyAll();
        }
    }

    // the services that this decorator actually provides
    public void startMonitoring(JobId id) {
        if (mapOfJobs.containsKey(id))
            throw new IllegalArgumentException("The listener already monitors the execution of job " + id);
        mapOfJobs.put(id, new ConnectorJobInfo());
    }

    public void stopMonitoring(JobId id) {
        mapOfJobs.remove(id);
    }

    public boolean jobFinished(JobId jobId) {
        ConnectorJobInfo info = mapOfJobs.get(jobId);
        if (info == null)
            throw new IllegalArgumentException("The listener does not monitor the execution of job " + jobId);
        boolean ret;
        synchronized (info.lock) {
            ret = info.jobFinished;
        }
        return ret;
    }

    public void waitJobFinished(JobId id) throws InterruptedException {
        ConnectorJobInfo info = mapOfJobs.get(id);
        if (info == null)
            throw new IllegalArgumentException("The listener does not monitor the execution of job " + id);
        synchronized (info.lock) {
            while (!info.jobFinished) {
                info.lock.wait();
            }
        }
    }

}