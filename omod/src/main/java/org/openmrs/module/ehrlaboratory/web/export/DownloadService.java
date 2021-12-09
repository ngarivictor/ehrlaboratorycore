/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */

package org.openmrs.module.ehrlaboratory.web.export;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.openmrs.Concept;
import org.openmrs.api.context.Context;
import org.openmrs.module.hospitalcore.model.LabTest;
import org.openmrs.module.ehrlaboratory.LaboratoryService;
import org.openmrs.module.ehrlaboratory.util.LaboratoryConstants;
import org.openmrs.module.ehrlaboratory.util.LaboratoryUtil;
import org.openmrs.module.ehrlaboratory.util.TestModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.SessionAttributes;

@Service("downloadService")
@Transactional
@SessionAttributes("adts")
public class DownloadService {
	public List<TestModel> getDatasource(@ModelAttribute ExportAttributeDetailsApi adts,
			HttpServletRequest request) throws ParseException {
		String dateStr = adts.getDateStr();
		String phrase = adts.getPhrase();
		int investigationId = adts.getInvestigationId();
		String showResults = adts.getShowResults();
		LaboratoryService ls = (LaboratoryService) Context
				.getService(LaboratoryService.class);
		Concept investigation = Context.getConceptService().getConcept(
				investigationId);
		SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
		Date date;

		date = sdf.parse(dateStr);
		Map<Concept, Set<Concept>> testTreeMap = (Map<Concept, Set<Concept>>) request
				.getSession().getAttribute(
						LaboratoryConstants.SESSION_TEST_TREE_MAP);
		Set<Concept> allowableTests = new HashSet<Concept>();
		if (investigation != null) {
			allowableTests = testTreeMap.get(investigation);
		} else {
			for (Concept c : testTreeMap.keySet()) {
				allowableTests.addAll(testTreeMap.get(c));
			}
		}
		List<LabTest> laboratoryTests = ls.getAllLaboratoryTestsByDate(date,
				phrase, allowableTests);
		List<TestModel> tests = LaboratoryUtil
				.generateModelsForPrintWorkListFromTests(laboratoryTests,
						testTreeMap, showResults);

		Collections.sort(tests);
		return tests;

	}

	public void downloadXLS(@ModelAttribute ExportAttributeDetailsApi adts,
			HttpServletRequest request, HttpServletResponse response)
			throws ClassNotFoundException, ParseException {

		// 1. Create new workbook
		HSSFWorkbook workbook = new HSSFWorkbook();

		// 2. Create new worksheet
		HSSFSheet worksheet = workbook.createSheet("Patient Lab Result Report");

		// 3. Define starting indices for rows and columns
		int startRowIndex = 0;
		int startColIndex = 0;

		// 4. Build layout
		// Build title, date, and column headers
		ExportLayouter.buildReport(worksheet, startRowIndex, startColIndex);

		// 5. Fill report
		ExportFillManager.fillReport(worksheet, startRowIndex, startColIndex,
				getDatasource(adts, request));

		// 6. Set the response properties
		String months[] = { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul",
				"Aug", "Sep", "Oct", "Nov", "Dec" };
		//ghanshyam 4-oct-2012 Support #405 [Laboratory]Export workList excel sheet name should include the investigation date not current date
		String dateStr = adts.getDateStr();
		SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
		Date date=new Date();
		date = sdf.parse(dateStr);
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		String day=Integer.toString(date.getDate());
		String month=months[date.getMonth()];
		String year=Integer.toString(calendar.get(Calendar.YEAR));
		String dayMonthYear=day+"-"+month+"-"+year;
		String fileName = "PatientLabResultReport" + dayMonthYear + ".xls";
		response.setHeader("Content-Disposition", "inline; filename="
				+ fileName);
		// Make sure to set the correct content type
		response.setContentType("application/vnd.ms-excel");

		// 7. Write to the output stream
		ExportWriter.write(response, worksheet);
	}
}
