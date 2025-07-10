package ext.sami.changenotice;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import com.ptc.core.lwc.server.PersistableAdapter;
import com.ptc.netmarkets.model.NmOid;
import com.ptc.netmarkets.util.misc.NetmarketURL;
import com.ptc.netmarkets.util.misc.NmActionServiceHelper;
import com.ptc.windchill.enterprise.workflow.WorkflowCommands;

import wt.change2.WTChangeOrder2;
import wt.fc.ObjectReference;
import wt.fc.Persistable;
import wt.fc.PersistenceHelper;
import wt.fc.QueryResult;
import wt.fc.ReferenceFactory;
import wt.httpgw.URLFactory;
import wt.inf.container.WTContainer;
import wt.lifecycle.LifeCycleState;
import wt.mail.EMailMessage;
import wt.org.WTPrincipalReference;
import wt.pds.StatementSpec;
import wt.project.Role;
import wt.query.QuerySpec;
import wt.query.SearchCondition;
import wt.util.WTException;
import wt.workflow.engine.WfEngineHelper;
import wt.workflow.engine.WfProcess;
import wt.workflow.engine.WfState;
import wt.workflow.engine.WfVotingEventAudit;
import wt.workflow.work.WfAssignedActivity;
import wt.workflow.work.WfAssignment;
import wt.workflow.work.WfAssignmentState;
import wt.workflow.work.WorkItem;

public class SendMailFromChangeNotice {

	public static String sendmail(Object primaryBusinessbject, ObjectReference self) {

		try {

			WTChangeOrder2 cn = null;
			String userMail = "";
			String assignefullname = "";
			WTContainer product = null;
			String role = "";
			Object currentVote = "";
			LifeCycleState cnstate = null;
			WfProcess wfProcess = null;
			WTPrincipalReference creatoruser = null;
			String CALink = "";
			String task_comment = "";
			WfAssignmentState workitemstatus = null;
			if (primaryBusinessbject instanceof WTChangeOrder2) {
				cn = (WTChangeOrder2) primaryBusinessbject;
			}
			creatoruser = cn.getCreator();
			product = cn.getContainer();
			cnstate = cn.getState();
			QueryResult qr = WfEngineHelper.service.getAssociatedProcesses(cn, WfState.OPEN,
					cn.getContainerReference());
			while (qr.hasMoreElements()) {

				wfProcess = (WfProcess) qr.nextElement();
			}
			QuerySpec qs = new QuerySpec(WfAssignedActivity.class);
			qs.appendWhere(new SearchCondition(WfAssignedActivity.class, "parentProcessRef.key.id",
					SearchCondition.EQUAL, wfProcess.getPersistInfo().getObjectIdentifier().getId()), new int[] { 0 });
			QueryResult qr1 = PersistenceHelper.manager.find((StatementSpec) qs);
			while (qr1.hasMoreElements()) {
				WfAssignedActivity act = (WfAssignedActivity) qr1.nextElement();
				String taskname = act.getName();
				System.out.println("taskname: " + taskname);
				wt.workflow.engine.ProcessData actData = act.getContext();
				task_comment = actData.getTaskComments();
				currentVote = act.getContext().getValue("WfUserEventList");
				if (task_comment==null) {
					
					task_comment = "";
				}
				if (currentVote==null) {
				
					currentVote = "";
				}
				String[] stockArr = null;
				wt.fc.QueryResult wfassignments = wt.fc.PersistenceHelper.manager.navigate(act,
						wt.workflow.work.ActivityAssignmentLink.ASSIGNMENT_ROLE,
						wt.workflow.work.ActivityAssignmentLink.class);
				while (wfassignments.hasMoreElements()) {
					wt.workflow.work.WfAssignment wfassignment = (wt.workflow.work.WfAssignment) wfassignments
							.nextElement();
					java.util.Enumeration<?> workitems = wt.fc.PersistenceHelper.manager.navigate(wfassignment,
							wt.workflow.work.WorkItemLink.WORK_ITEM_ROLE, wt.workflow.work.WorkItemLink.class);
					while (workitems.hasMoreElements()) {
						wt.workflow.work.WorkItem workitem = (wt.workflow.work.WorkItem) workitems.nextElement();
						workitemstatus = workitem.getStatus();
						role = workitem.getRole().getFullDisplay();
						CALink = getInfoPageURL(workitem);
						ArrayList<String> emaillist = new ArrayList<String>();
						WTPrincipalReference user = workitem.getOwnership().getOwner();
						userMail = user.getEMail();
						assignefullname = user.getFullName();
						emaillist.add(userMail);
						stockArr = new String[emaillist.size()];
						stockArr = emaillist.toArray(stockArr);
						if (WfAssignmentState.POTENTIAL.equals(workitemstatus)) {
							sendMailForRunningTask(CALink, taskname, cn, role, assignefullname, product, cnstate,
									stockArr, creatoruser);
						} else if (WfAssignmentState.COMPLETED.equals(workitemstatus)) {
							sendMailForCompleteTask(assignefullname, cn, product, role, task_comment, stockArr,
									creatoruser, currentVote,taskname);
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static Object getIBA(Persistable persit, String attrname) {
		PersistableAdapter persist;
		try {
			persist = new PersistableAdapter(persit, null, null, null);
			persist.load(attrname);
			Object obj = persist.get(attrname);

			return obj;

		} catch (WTException e) {

			e.printStackTrace();
		}
		return null;
	}

	public static String getInfoPageURL(Persistable persistable) throws WTException {
		try {
			ReferenceFactory referencefactory = new ReferenceFactory();
			String oid = referencefactory.getReferenceString(persistable);
			URLFactory urlfactory = new URLFactory();
			HashMap<String, String> map = new HashMap<String, String>();
			map.put("oid", oid);
			String action = NmActionServiceHelper.service.getAction("object", "view").getUrl();

			return urlfactory.getHREF(action, map, true);
		} catch (WTException e) {

			e.printStackTrace();
		}
		return null;
	}

	public static WfProcess getprocess(WTChangeOrder2 activitys) {
		try {
			QueryResult processes = WfEngineHelper.service.getAssociatedProcesses(activitys, null, null);
			WfProcess process = null;
			int size = processes.size();
			while (processes.hasMoreElements()) {
				process = (wt.workflow.engine.WfProcess) processes.nextElement();
				String process_Status = process.getState().toString();
				if (process_Status.equals("CLOSED_COMPLETED_EXECUTED") || process_Status.equals("OPEN_RUNNING")) {
					if (process != null) {
						return process;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;

	}

	public static void sendMailForRunningTask(String CALink, String taskname, WTChangeOrder2 cn, String role,
			String assignefullname, WTContainer product, LifeCycleState cnstate, String[] stockArr,
			WTPrincipalReference creatoruser) {
		try {
           String productdescription=product.getDescription();
           Object needdate=getIBA(cn, "needDate");
           if (productdescription==null) {
	
	         productdescription = "";
               }
            if (needdate==null) {
		
	       	needdate = "";
                 }
			String mailBodyStart = " <table><table>";
			mailBodyStart = mailBodyStart + "<table>"

					+ "<tr><td><b>You have been assigned a :</b> <a href=" + CALink + ">" + taskname + "</a></td></tr>"
					+ "<tr><td><b>Instructions : </b>"
					+ "A Change notice has been submitted that requires analysis to determine if further action is required.\r\n<br>"
					+ "1. To complete the analysis:\r\n<br>" + "a. Read the Special Instructions below, if any.\r\n<br>"
					+ "b. Review the information page of the change notice in the subject.\r\n<br>"
					+ "c. Coordinate the technical analysis and generation of recommended solution.<br>"
					+ "2. To complete this assignment:\r\n<br>"
					+ "a. (Optional) Enter comments in the Comments field below.\r\n<br>"
					+ "b. (Optional) Enter specific directions for the next task in the Special Instructions field.\r\n<br>"
					+ "c. Determine the routing of the change notice.  Click Fast_Track, Full_Track, Clarify, Reassign,Reject, or Review.\r\n<br>"
					+ "d. Click Complete Task below to advance the change notice or click Save to advance it at a later time"
					+ "</td></tr>" + "<tr><td><b>Process Initiator : </b>" + cn.getCreatorFullName() + "</td></tr>"
					+ "<tr><td><b>Need Date : </b>" + needdate + "</td></tr>" + "<tr><td><b>Role : </b>"
					+ role + "</td></tr>" + "<tr><td><b>Assignee : </b>" + assignefullname + "</td></tr>"
					+ "<tr><td><b>Product Name : </b>" + cn.getContainerName() + "</td></tr>"
					+ "<tr><td><b>Product Creator : </b>" + product.getCreator().getName() + "</td></tr>"
					+ "<tr><td><b>Host Organization : </b>" + product.getOrganizationName() + "</td></tr>"
					+ "<tr><td><b>Product Description : </b>" + productdescription + "</td></tr>"
					+ "<tr><td><b>CN Name : </b>" + cn.getName() + "</td></tr>" + "<tr><td><b>CN Number : </b>"
					+ cn.getNumber() + "</td></tr>" + "<tr><td><b>CN State : </b>" + cnstate + "</td></tr>"
					+ "<tr><td><b>CN Created On : </b>" + cn.getCreateTimestamp() + "</td></tr>"

					+ "</table>";
			
			EMailMessage localEMailMessage = EMailMessage.newEMailMessage();
			localEMailMessage.addEmailAddress(stockArr);
			localEMailMessage.setSubject(taskname +" "+":"+ cn.getNumber());
			localEMailMessage.addPart(mailBodyStart, "text/html");
			localEMailMessage.setOriginator(creatoruser);
			localEMailMessage.send(true);

			System.out.println("EMailMessage in open task......");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void sendMailForCompleteTask(String assignefullname, WTChangeOrder2 cn, WTContainer product,
			String role, String task_comment, String[] stockArr, WTPrincipalReference creatoruser, Object currentVote,String taskname) {
		try {
			 String productdescription=product.getDescription();
	           Object needdate=getIBA(cn, "needDate");
	           if (productdescription==null) {
		
		         productdescription = "";
	               }
	            if (needdate==null) {
			
		       	needdate = "";
	                 }
			String mailBodyStart = " <table><table>";
			mailBodyStart = mailBodyStart + "<table>" + "<tr><td><b>Process Initiator : </b>" + cn.getCreatorFullName()
					+ "</td></tr>" + "<tr><td><b>Need Date : </b>" + needdate + "</td></tr>"
					+ "<tr><td><b>Assignee : </b>" + assignefullname + "</td></tr>" + "<tr><td><b>Product Name : </b>"
					+ cn.getContainerName() + "</td></tr>" + "<tr><td><b>Product Creator : </b>"
					+ product.getCreator().getName() + "</td></tr>" + "<tr><td><b>Host Organization : </b>"
					+ product.getOrganizationName() + "</td></tr>" + "<tr><td><b>Product Description : </b>"
					+ productdescription + "</td></tr>" + "<tr><td><b>Role : </b>" + role + "</td></tr>"
					+ "<tr><td><b>CN Name : </b>" + cn.getName() + "</td></tr>" + "<tr><td><b>CN Number : </b>"
					+ cn.getNumber() + "</td></tr>" + "<tr><td><b>CN State : </b>" + cn.getState() + "</td></tr>"
					+ "<tr><td><b>CN Created On : </b>" + cn.getCreateTimestamp() + "</td></tr>"
					+ "<tr><td><b>Task Completion Comment : </b>" + task_comment + "</td></tr>"
					+ "<tr><td><b>Vote/Route : </b>" + currentVote + "</td></tr>"

					+ "</table>";
			EMailMessage localEMailMessage = EMailMessage.newEMailMessage();
			localEMailMessage.addEmailAddress(stockArr);
			localEMailMessage.setSubject(taskname +" "+"is Completed"+":" + cn.getNumber());
			localEMailMessage.addPart(mailBodyStart, "text/html");
			localEMailMessage.setOriginator(creatoruser);
			localEMailMessage.send(true);
			System.out.println("EMailMessage in closed task......");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
