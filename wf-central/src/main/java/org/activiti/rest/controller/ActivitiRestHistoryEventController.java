package org.activiti.rest.controller;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.activiti.engine.impl.util.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.simple.JSONValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.wf.dp.dniprorada.base.dao.BaseEntityDao;
import org.wf.dp.dniprorada.base.dao.EntityNotFoundException;
import org.wf.dp.dniprorada.base.dao.GenericEntityDao;
import org.wf.dp.dniprorada.base.util.JsonRestUtils;
import org.wf.dp.dniprorada.constant.HistoryEventMessage;
import org.wf.dp.dniprorada.constant.HistoryEventType;
import org.wf.dp.dniprorada.dao.DocumentDao;
import org.wf.dp.dniprorada.dao.HistoryEventDao;
import org.wf.dp.dniprorada.dao.HistoryEvent_ServiceDao;
import org.wf.dp.dniprorada.model.HistoryEvent;
import org.wf.dp.dniprorada.model.HistoryEvent_Service;
import org.wf.dp.dniprorada.model.Region;
import org.wf.dp.dniprorada.model.Service;
import org.wf.dp.dniprorada.model.ServiceData;
import org.wf.dp.dniprorada.rest.HttpRequester;
import org.wf.dp.dniprorada.util.GeneralConfig;
import org.wf.dp.dniprorada.util.luna.AlgorithmLuna;
import org.wf.dp.dniprorada.util.luna.CRCInvalidException;

@Controller
@RequestMapping(value = "/services")
public class ActivitiRestHistoryEventController {

	private static final Logger log = Logger.getLogger(ActivitiRestHistoryEventController.class);

	@Autowired
	private BaseEntityDao baseEntityDao;
	
	@Autowired
	private HistoryEvent_ServiceDao historyEventServiceDao;

	@Autowired
	private HistoryEventDao historyEventDao;
	
	@Autowired
	@Qualifier("regionDao")
	private GenericEntityDao<Region> regionDao;

	@Autowired
	private DocumentDao documentDao;
	
    @Autowired
    private GeneralConfig generalConfig;
    
    @Autowired
    private HttpRequester httpRequester;
	
    @Autowired
	@Qualifier("serviceDataDao")
	private GenericEntityDao<ServiceData> serviceDataDao;
    
	/**
	 * check the correctness of nID_Protected (by algorithm Luna) and return
	 * the object of HistoryEvent_Service
	 * @param nID_Protected  -- string ID of event
	 * @return the object (if nID is correct and record exists) otherwise return
	 *         403. CRC Error (wrong nID_Protected) or 403. "Record not found"
	 */
	@RequestMapping(value = "/getHistoryEvent_Service", method = RequestMethod.GET)
	public @ResponseBody
	ResponseEntity<String> getHistoryEvent_Service(
			@RequestParam(value = "nID_Protected") Long nID_Protected)
			throws ActivitiRestException {

		HistoryEvent_Service event_service = null;
		ResponseEntity<String> result;
		try {
			event_service = historyEventServiceDao
					.getHistoryEvent_ServiceByID_Protected(nID_Protected);
			result = JsonRestUtils.toJsonResponse(event_service);
		} catch (EntityNotFoundException e) {
			ActivitiRestException newErr = new ActivitiRestException(
					"BUSINESS_ERR", "Record not found", e);
			newErr.setHttpStatus(HttpStatus.FORBIDDEN);
			throw newErr;
		} catch (CRCInvalidException e) {
			ActivitiRestException newErr = new ActivitiRestException(
					"BUSINESS_ERR", e.getMessage(), e);
			newErr.setHttpStatus(HttpStatus.FORBIDDEN);
			throw newErr;
		} catch (RuntimeException e) {
			throw new ActivitiRestException(e.getMessage(), e);
		}
		return result;
	}


	/**
	 * add the object of HistoryEvent_Service to db
	 * @param nID_Subject-- SubjectID (optional)
	 * @param sID_Status-- string-status (optional, for algorithm Luna)
	 * @return the created object
	 */
	@RequestMapping(value = "/addHistoryEvent_Service", method = RequestMethod.GET)
	public @ResponseBody
	ResponseEntity<String> addHistoryEvent_Service(
			@RequestParam(value = "nID_Process") Long nID_Process,
			@RequestParam(value = "nID_Subject") Long nID_Subject,
			@RequestParam(value = "sID_Status") String sID_Status,
			@RequestParam(value = "sProcessInstanceName") String sProcessInstanceName,
			@RequestParam(value = "nID_Service", required=false) Long nID_Service,
			@RequestParam(value = "nID_Region", required=false) Long nID_Region ,
			@RequestParam(value = "sID_UA", required = false) String sID_UA,
			@RequestParam(value = "soData", required = false) String soData,
			@RequestParam(value = "sToken", required = false) String sToken,
			@RequestParam(value = "sHead", required = false) String sHead,
			@RequestParam(value = "sBody", required = false) String sBody) {

		HistoryEvent_Service event_service = historyEventServiceDao.addHistoryEvent_Service(nID_Process, sID_Status, nID_Subject,
				sID_Status, nID_Service, nID_Region, sID_UA, 0,
				soData, sToken, sHead, sBody);
		//get_service history event
		Map<String, String> mParamMessage = new HashMap<>();
		mParamMessage.put(HistoryEventMessage.SERVICE_NAME, sProcessInstanceName);
		mParamMessage.put(HistoryEventMessage.SERVICE_STATE, sID_Status);
		setHistoryEvent(HistoryEventType.GET_SERVICE, nID_Subject, mParamMessage);
		//My journal. setTaskQuestions (issue 808)
		createHistoryEventForTaskQuestions(HistoryEventType.SET_TASK_QUESTIONS, soData, soData, event_service.getnID_Protected(), nID_Subject);
		return JsonRestUtils.toJsonResponse(event_service);
	}

	/**
	 * check the correctness of nID_Protected (by algorithm Luna) and update the
	 * object of HistoryEvent_Service in db
	 //	 * @param nID_Protected-- nID_Protected of event_service
	 //	 * @param sStatus-- string of status
	 * @param sID_Status -- string-status (optional)
	 *            @return 200ok or "Record not found"
	 */
	@RequestMapping(value = "/updateHistoryEvent_Service", method = RequestMethod.GET)
	public @ResponseBody
	HistoryEvent_Service updateHistoryEvent_Service(
			@RequestParam(value = "nID_Process") Long nID_Process,
			@RequestParam(value = "sID_Status") String sID_Status,
			@RequestParam(value = "soData", required = false) String soData,
			@RequestParam(value = "sToken", required = false) String sToken,
			@RequestParam(value = "sHead", required = false) String sHead,
			@RequestParam(value = "sBody", required = false) String sBody) {
		Long nID_Protected = AlgorithmLuna.getProtectedNumber(nID_Process);
		Long nID_Subject = historyEventServiceDao.getHistoryEvent_ServiceBynID_Task(nID_Process).getnID_Subject();
		HistoryEvent_Service event_service = historyEventServiceDao
				.getHistoryEvent_ServiceBynID_Task(nID_Process);
		if (event_service != null) {
			boolean isChanged = false;
			if (sID_Status != null
					&& !sID_Status.equals(event_service.getsID_Status())) {
				event_service.setsID_Status(sID_Status);
				isChanged = true;
			}
			if (soData != null
					&& !soData.equals(event_service.getSoData())) {
				event_service.setSoData(soData);
				isChanged = true;
				if (sHead == null){
					sHead = "Необхідно уточнити дані";
				}
			}
			if (sHead != null
					&& !sHead.equals(event_service.getsHead())) {
				event_service.setsHead(sHead);
				isChanged = true;
			}
			if (sBody != null
					&& !sBody.equals(event_service.getsBody())) {
				event_service.setsBody(sBody);
				isChanged = true;
			}
			if (sToken == null || !sToken.equals(event_service.getsToken())) {
				event_service.setsToken(sToken);
				isChanged = true;
			}
			if (isChanged) {
				historyEventServiceDao.updateHistoryEvent_Service(event_service);
			}
		}
		//My journal. change status of task
		Map<String, String> mParamMessage = new HashMap<>();
		mParamMessage.put(HistoryEventMessage.SERVICE_STATE, sID_Status);
		mParamMessage.put(HistoryEventMessage.TASK_NUMBER, String.valueOf(nID_Protected));
		setHistoryEvent(HistoryEventType.ACTIVITY_STATUS_NEW, nID_Subject, mParamMessage);
		//My journal. setTaskQuestions (issue 808, 809)
		log.info(">>>> 1 try create history event for SET_TASK_QUESTIONS.");

		if (soData != null) {
			log.info(">>>> 2 try create history event for SET_TASK_QUESTIONS.");

			createHistoryEventForTaskQuestions(sToken != null ? HistoryEventType.SET_TASK_QUESTIONS : HistoryEventType.SET_TASK_ANSWERS,
					soData, sBody, nID_Protected, nID_Subject);
		}
		return event_service;
	}

	private void createHistoryEventForTaskQuestions(HistoryEventType eventType, String soData, String data, Long nID_Protected, Long nID_Subject) {
		Map<String, String> mParamMessage = new HashMap<>();
		if (soData != null && !"[]".equals(soData) ) {
			log.info(">>>>create history event for SET_TASK_QUESTIONS.");
			log.info(">>>>create history event for SET_TASK_QUESTIONS.TASK_NUMBER=" + nID_Protected);
			mParamMessage.put(HistoryEventMessage.TASK_NUMBER, "" + nID_Protected);
			log.info(">>>>create history event for SET_TASK_QUESTIONS.data=" + data);
			mParamMessage.put(HistoryEventMessage.S_BODY, data == null ? "" : data);
			log.info(">>>>create history event for SET_TASK_QUESTIONS.TABLE_BODY=" + HistoryEventMessage.createTable(soData));
			mParamMessage.put(HistoryEventMessage.TABLE_BODY, HistoryEventMessage.createTable(soData));
			log.info(">>>>create history event for SET_TASK_QUESTIONS.nID_Subject=" + nID_Subject);
			setHistoryEvent(eventType, nID_Subject, mParamMessage);
			log.info(">>>>create history event for SET_TASK_QUESTIONS... ok!");
		}
	}


	//################ HistoryEvent services ###################

	@RequestMapping(value = "/setHistoryEvent", method = RequestMethod.POST)
	public @ResponseBody
	Long setHistoryEvent(
			@RequestParam(value = "nID_Subject", required = false) long nID_Subject,
			@RequestParam(value = "nID_HistoryEventType", required = false) Long nID_HistoryEventType,
			@RequestParam(value = "sEventName", required = false) String sEventName_Custom,
			@RequestParam(value = "sMessage") String sMessage,

			HttpServletRequest request, HttpServletResponse httpResponse)
			throws IOException {

		return historyEventDao.setHistoryEvent(nID_Subject,
				nID_HistoryEventType, sEventName_Custom, sMessage);

	}
	
	@RequestMapping(value = "/getHistoryEvent", method = RequestMethod.GET)
	public @ResponseBody
	HistoryEvent getHistoryEvent(@RequestParam(value = "nID") Long id) {
		return historyEventDao.getHistoryEvent(id);
	}

	@RequestMapping(value = "/getHistoryEvents", method = RequestMethod.GET)
	public @ResponseBody
	List<HistoryEvent> getHistoryEvents(
			@RequestParam(value = "nID_Subject") long nID_Subject) {
		return historyEventDao.getHistoryEvents(nID_Subject);
	}
	
	
	@RequestMapping(value = "/getStatisticServiceCounts", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
	public @ResponseBody
	String getStatisticServiceCounts(@RequestParam(value = "nID_Service") Long nID_Service) {

		List<Map<String, Object>> listOfHistoryEventsWithMeaningfulNames = getListOfHistoryEvents(nID_Service);
		return JSONValue.toJSONString(listOfHistoryEventsWithMeaningfulNames);
	}

	private List<Map<String, Object>> getListOfHistoryEvents(Long nID_Service){
		
		List<ServiceData> serviceDataList = null;
		try {
			serviceDataList = serviceDataDao.findAllBy("service.id", nID_Service);
			log.info("serviceDataList:" + serviceDataList.size());
			for (ServiceData data : serviceDataList){
				String sIDUA = null;
				if (data.getCity() != null && data.getCity().getRegion() != null) 
					sIDUA = data.getCity().getRegion().getsID_UA();
				log.info(data.getId() + ":" + sIDUA + ":" + data.getRegion() + ":" + data.getData());
			}
		} catch (Exception e){
			e.printStackTrace();
		}
		
		List<Map<String, Object>> listOfHistoryEventsWithMeaningfulNames = new LinkedList<Map<String, Object>>();
		List<Map<String, Long>> listOfHistoryEvents = historyEventServiceDao.getHistoryEvent_ServiceBynID_Service(nID_Service);
		Map<String, Object> currMapWithName;
		Region region;
		Long nRate;
		Long nCount;
		for (Map<String, Long> currMap : listOfHistoryEvents){
			currMapWithName = new HashMap<>();

			region = regionDao.findByIdExpected(currMap.get("sName"));
			String averageDuration = null;
			if (serviceDataList != null){
				log.info("comparing region:" + region.getName() + ":" + region.getsID_UA() + ":" + region.getId());
				String bpName = findNameOfBPForRegion(region.getsID_UA(), serviceDataList);
				if (bpName != null){
					averageDuration = averageDurationOfBusinessProcess(bpName);
				}
			}
			
			log.info("[getListOfHistoryEvents]sName=" + region.getName());
			currMapWithName.put("sName", region.getName());

			nRate = currMap.get("nRate") == null ? 0L : currMap.get("nRate");
			nCount = currMap.get("nCount") == null ? 0L : currMap.get("nCount");

			nCount = addSomeServicesCount(nCount, nID_Service, region);

			if (nID_Service == 159) {//issue 750 + 777
				log.info("[getListOfHistoryEvents]!!!nID_Service=" + nID_Service);
				List<Map<String, Object>> am;
				Long[] arr;
				Long nSumRate = nRate * nCount;
				for (Long nID = 726L; nID < 734L; nID++) {
					am = getListOfHistoryEvents(nID);
					arr = getCountFromStatisticArrayMap(am);
					nCount += arr[0];
					nSumRate += arr[1];
				}
				log.info("[getListOfHistoryEvents]nCount(summ)=" + nCount);
				nRate = nSumRate / nCount;
				log.info("[getListOfHistoryEvents]nRAte(summ)=" + nRate);
			}
			log.info("[getListOfHistoryEvents]nCount=" + nCount);
			currMapWithName.put("nCount", nCount);
			currMapWithName.put("nRate", nRate);
			currMapWithName.put("nTimeHours", averageDuration != null ? averageDuration : "-1");
			listOfHistoryEventsWithMeaningfulNames.add(currMapWithName);
		}
		return listOfHistoryEventsWithMeaningfulNames;
	}

	private String findNameOfBPForRegion(String regionID_UA,
			List<ServiceData> serviceDataList) {
		String res = null;
		log.info("Comparing region " + regionID_UA);
		for (ServiceData serviceData : serviceDataList){
			if (serviceData.getCity() == null || serviceData.getCity().getRegion() == null || serviceData.getCity().getRegion().getsID_UA() == null){
				log.info("Skipping service data:" + serviceData.getId());
				continue;
			}
			log.info("Comparing region with service data for region:" + serviceData.getCity().getsID_UA() + ":" + serviceData.getRegion() + ":" + 
					(serviceData.getCity() != null ? serviceData.getCity().getRegion().getsID_UA() : ""));
			if (regionID_UA.equals(serviceData.getCity().getRegion().getsID_UA())){
				String dataJson = serviceData.getData();
        		log.info("Found data for the service data:" + dataJson);

		        JSONObject jsonMap = new JSONObject(dataJson);
		        if (jsonMap.has("oParams")){
		        	JSONObject jsonProcessDefinitionIdMap = jsonMap.getJSONObject("oParams");
		        	log.info("Value of oParams:" + jsonProcessDefinitionIdMap);
		        	if (jsonProcessDefinitionIdMap.has("processDefinitionId")){
		        		String processDefinitionId = (String) jsonProcessDefinitionIdMap.get("processDefinitionId");
		        		log.info("Found process definiton ID " + processDefinitionId + " for the region " + regionID_UA);
		        		return StringUtils.substringBefore(processDefinitionId, ":");
		        	}
		        }
			}
		}
		return res;
	}

	private String averageDurationOfBusinessProcess(String sBPName) {
		String URI = "/wf/service/rest/process-duration";
		Map<String, String> params = new HashMap<>();
		params.put("sID_BP_Name", sBPName);
		Calendar currDate = Calendar.getInstance();
		SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");
		params.put("sDateTo", sdfDate.format(currDate.getTime()));
		currDate.add(Calendar.MONTH, -3);
		params.put("sDateAt", sdfDate.format(currDate.getTime()));
		String host = generalConfig.sHost();
		if (host.indexOf("region") == -1){
			host = StringUtils.replace(host, "igov", "region.igov");
		}
		log.info("Getting URL with parameters: " + host + URI
				+ params + ":" + generalConfig.sHostCentral());
		String soJSON_Duration;
		try {
			soJSON_Duration = httpRequester.get(host + URI, params);
			log.info("soJSON_Duration=" + soJSON_Duration);
			JSONObject jsonMap = new JSONObject(soJSON_Duration);
			if (jsonMap.has(sBPName)) {
				return jsonMap.getString(sBPName);
			}
		} catch (Exception e) {
			log.error("Exception occured while getting aberage duration for business process:" + e.getMessage());
		}
		return null;
	}
	

	private Long addSomeServicesCount(Long nCount, Long nID_Service, Region region) {
		//currMapWithName.put("nCount", currMap.get("nCount"));
			  /*https://igov.org.ua/service/661/general - 43
				https://igov.org.ua/service/655/generall - 75
				https://igov.org.ua/service/176/general - 546
				https://igov.org.ua/service/654/general - 307   */

		if (nID_Service == 661) {
			if ("1200000000".equals(region.getsID_UA()) || "1200000000".equals(region.getsID_UA())) {
				nCount += 43;
			}
		} else if (nID_Service == 665) {
			if ("1200000000".equals(region.getsID_UA()) || "1200000000".equals(region.getsID_UA())) {
				nCount += 75;
			}
		} else if (nID_Service == 176) {
			if ("1200000000".equals(region.getsID_UA()) || "1200000000".equals(region.getsID_UA())) {
				nCount += 546;
			}
		} else if (nID_Service == 654) {
			if ("1200000000".equals(region.getsID_UA()) || "1200000000".equals(region.getsID_UA())) {
				nCount += 307;
			}
		} else if (nID_Service == 159) {
				/*https://igov.org.ua/service/159/general
				Днепропетровская область - 53
                Киевская область - 69
                1;Дніпропетровська;"1200000000"
                5;Київ;"8000000000"
                16;Київська;"3200000000"*/
			if ("1200000000".equals(region.getsID_UA()) || "1200000000".equals(region.getsID_UA())) {
				nCount += 53;
			} else if ("8000000000".equals(region.getsID_UA()) || "3200000000".equals(region.getsID_UA())) {
				nCount += 69;
			}
		} else if (nID_Service == 1) {
			 /*https://igov.org.ua/service/1/general
			Днепропетровская область - 812*/
			  /*if("".equals(region.getsID_UA())){
				nCount+=53;
              }else if("".equals(region.getsID_UA())){
                nCount+=69;
              }*/
			if ("1200000000".equals(region.getsID_UA()) || "1200000000".equals(region.getsID_UA())) {
				nCount += 812;
			}
		} else if (nID_Service == 772) {
			if ("1200000000".equals(region.getsID_UA()) || "1200000000".equals(region.getsID_UA())) {
				nCount += 96;
			}
		} else if (nID_Service == 4) {
			  /*
			https://igov.org.ua/service/4/general -
            Днепропетровская область - услуга временно приостановлена
            по иным регионам заявок вне было.
              */
			nCount += 0;
		} else if (nID_Service == 0) {
			nCount += 0;
			//region.getsID_UA()
		}
		return nCount;
	}


	private Long[] getCountFromStatisticArrayMap(List<Map<String, Object>> am) {
		Long n = 0L;
		Long nRate = 0L;
		log.info("[getCountFromStatisticArrayMap] am=" + am);
		if (am.size() > 0) {
			if (am.get(0).containsKey("nCount")) {
				String s = am.get(0).get("nCount") + "";
				if (!"null".equals(s)) {
					n = new Long(s);
					log.info("[getCountFromStatisticArrayMap] n=" + n);
				}
			}
			if (am.get(0).containsKey("nRate")) {
				String s = am.get(0).get("nRate") + "";
				if (!"null".equals(s)) {
					nRate = new Long(s);
					log.info("[getCountFromStatisticArrayMap] nRate=" + n);
				}
			}
		}
		return new Long[]{n, nRate * n};
	}


	private void setHistoryEvent(HistoryEventType eventType,
			Long nID_Subject, Map<String, String> mParamMessage) {
		try {
			String eventMessage = HistoryEventMessage.createJournalMessage(
					eventType, mParamMessage);
			historyEventDao.setHistoryEvent(nID_Subject, eventType.getnID(),
					eventMessage, eventMessage);
		} catch (IOException e) {
			log.error("error during creating HistoryEvent", e);
		}
	}

}
