/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */

package org.openmrs.module.ehrlaboratory.web.controller.patientreport;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.openmrs.Concept;
import org.openmrs.ConceptNumeric;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.hospitalcore.model.LabTest;
import org.openmrs.module.ehrlaboratory.LaboratoryService;
import org.openmrs.module.ehrlaboratory.util.LaboratoryConstants;
import org.openmrs.module.ehrlaboratory.util.LaboratoryUtil;
import org.openmrs.module.ehrlaboratory.util.TestResultModel;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller("LaboratorySearchPatientReportController")
@RequestMapping("/module/ehrlaboratory/searchPatientReport.form")
public class SearchPatientReportController {

	@SuppressWarnings("unchecked")
	@RequestMapping(method = RequestMethod.GET)
	public String searchTest(
			@RequestParam(value = "date", required = false) String dateStr,
			@RequestParam(value = "patientId") Integer patientId,
			HttpServletRequest request, Model model) {
		LaboratoryService ls = (LaboratoryService) Context
				.getService(LaboratoryService.class);
		SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
		Date date = null;
		try {
			date = sdf.parse(dateStr);
			Patient patient = Context.getPatientService().getPatient(patientId);
			if (patient != null) {

				List<LabTest> tests = ls.getLaboratoryTestsByDateAndPatient(
						date, patient);
				if ((tests != null) && (!tests.isEmpty())) {
					Map<Concept, Set<Concept>> testTreeMap = (Map<Concept, Set<Concept>>) request
							.getSession().getAttribute(
									LaboratoryConstants.SESSION_TEST_TREE_MAP);
					List<TestResultModel> trms = renderTests(tests, testTreeMap);
					trms = formatTestResult(trms);
					model.addAttribute("tests", trms);
				}
			}
		} catch (ParseException e) {
			e.printStackTrace();
			System.out.println("Error when parsing order date!");
			return null;
		}
		return "/module/ehrlaboratory/patientreport/search";
	}

	private List<TestResultModel> renderTests(List<LabTest> tests,
			Map<Concept, Set<Concept>> testTreeMap) {
		List<TestResultModel> trms = new ArrayList<TestResultModel>();
		for (LabTest test : tests) {
			if (test.getEncounter() != null) {
				Encounter encounter = test.getEncounter();
				for (Obs obs : encounter.getAllObs()) {
					TestResultModel trm = new TestResultModel();
					Concept investigation = getInvestigationByTest(test,
							testTreeMap);
					trm.setInvestigation(LaboratoryUtil
							.getConceptName(investigation));
					trm.setSet(test.getConcept().getName().getName());
					Concept concept = Context.getConceptService().getConcept(
							obs.getConcept().getConceptId());
					trm.setTest(concept.getName().getName());
					trm.setConcept(test.getConcept());
					setTestResultModelValue(obs, trm);
					trms.add(trm);
				}
			}
		}
		return trms;
	}

	private Concept getInvestigationByTest(LabTest test,
			Map<Concept, Set<Concept>> investigationTests) {
		for (Concept investigation : investigationTests.keySet()) {
			if (investigationTests.get(investigation).contains(
					test.getConcept()))
				return investigation;
		}
		return null;
	}

	private void setTestResultModelValue(Obs obs, TestResultModel trm) {
		Concept concept = Context.getConceptService().getConcept(
				obs.getConcept().getConceptId());
		trm.setTest(concept.getName().getName());
		if (concept != null) {
			String datatype = concept.getDatatype().getName();
			if (datatype.equalsIgnoreCase("Text")) {
				trm.setValue(obs.getValueText());
			} else if (datatype.equalsIgnoreCase("Numeric")) {
				if(obs.getValueText()!=null){
					trm.setValue(obs.getValueText().toString());	
				} else {
					trm.setValue(obs.getValueNumeric().toString());					
				}
				ConceptNumeric cn = Context.getConceptService()
						.getConceptNumeric(concept.getConceptId());
				trm.setUnit(cn.getUnits());
				if (cn.getLowNormal() != null)
					trm.setLowNormal(cn.getLowNormal().toString());
				if (cn.getHiNormal() != null)
					trm.setHiNormal(cn.getHiNormal().toString());
				if(cn.getHiAbsolute()!=null){
					trm.setHiAbsolute(cn.getHiAbsolute().toString());
				}
				if(cn.getHiCritical()!=null){
					trm.setHiCritical(cn.getHiCritical().toString());
				}
				if(cn.getLowAbsolute()!=null){
					trm.setLowAbsolute(cn.getLowAbsolute().toString());
				}
				if(cn.getLowCritical()!=null){
					trm.setLowCritical(cn.getLowCritical().toString());
				}

			} else if (datatype.equalsIgnoreCase("Coded")) {
				trm.setValue(obs.getValueCoded().getName().getName());
			}
		}
	}

	private List<TestResultModel> formatTestResult(
			List<TestResultModel> testResultModels) {
		Collections.sort(testResultModels);
		List<TestResultModel> trms = new ArrayList<TestResultModel>();
		String investigation = null;
		String set = null;
		for (TestResultModel trm : testResultModels) {
			if (!trm.getInvestigation().equalsIgnoreCase(investigation)) {
				investigation = trm.getInvestigation();
				TestResultModel t = new TestResultModel();
				t.setInvestigation(investigation);
				t.setLevel(TestResultModel.LEVEL_INVESTIGATION);
				set = null;
				trms.add(t);
			}

			if (!trm.getSet().equalsIgnoreCase(set)) {
				set = trm.getSet();
				if (!trm.getConcept().getConceptClass().getName()
						.equalsIgnoreCase("LabSet")) {
					trm.setLevel(TestResultModel.LEVEL_SET);
					trms.add(trm);
				} else {
					TestResultModel t = new TestResultModel();
					t.setSet(set);
					t.setLevel(TestResultModel.LEVEL_SET);
					t.setEncounterId(trm.getEncounterId());
					t.setTestId(trm.getTestId());
					trms.add(t);
				}
			}

			if (trm.getConcept().getConceptClass().getName()
					.equalsIgnoreCase("LabSet")) {
				trms.add(trm);
			}
		}
		return trms;
	}
}
