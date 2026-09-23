/**
 * 
 */
package de.unileipzig.irpsim.server.standingdata.excel;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.time.StopWatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.ConditionalFormattingRule;
import org.apache.poi.ss.usermodel.ConditionalFormattingThreshold.RangeType;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.IconMultiStateFormatting;
import org.apache.poi.ss.usermodel.IconMultiStateFormatting.IconSet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetConditionalFormatting;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFCreationHelper;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONObject;

import de.unileipzig.irpsim.core.simulation.data.TimeInterval;
import de.unileipzig.irpsim.core.standingdata.StammdatumEntity;
import de.unileipzig.irpsim.core.standingdata.SzenarioSet;
import de.unileipzig.irpsim.core.standingdata.SzenarioSetElement;
import de.unileipzig.irpsim.core.standingdata.data.Stammdatum;
import de.unileipzig.irpsim.core.utils.ParameterInputDependenciesUtil;

/**
 * Generate XLSX templates for timeseries data according to a 'Stammdatum'. Automatically generates one sheet per scenario and a cover page with metadata and a table showing the completion state of
 * each column in each data sheet.
 * 
 * @author sdienst
 *
 */
public class ImportTemplateGenerator {

   private static final Logger LOG = LogManager.getLogger(ImportTemplateGenerator.class);
   private static final SzenarioSet STANDARD = new SzenarioSet();

	static {
		final List<SzenarioSetElement> elements = new LinkedList<>();
		final SzenarioSetElement szenarioSetElement = new SzenarioSetElement();
		szenarioSetElement.setStelle(0);
		szenarioSetElement.setName("Standard");
		elements.add(szenarioSetElement);
		STANDARD.setSzenarien(elements);
	}

	private final XSSFWorkbook wb;
	private final String password;
	private final XSSFCellStyle editable;
	private boolean valid = false;
	private final XSSFCellStyle bold;
	private final XSSFCreationHelper ch;
	private int leapYearCount, nonLeapYearCount;
	private final SzenarioSet szenarien;
	final Stammdatum stammdatum;

	public ImportTemplateGenerator(final String password, final Stammdatum stammdatum, final SzenarioSet szenarien) {
		this.password = password;
		this.wb = new XSSFWorkbook();
		this.ch = this.wb.getCreationHelper();
		this.editable = wb.createCellStyle();
		editable.setLocked(false);

		final XSSFFont boldFont = wb.createFont();
		boldFont.setBold(true);
		this.bold = wb.createCellStyle();
		this.bold.setFont(boldFont);

		this.stammdatum = stammdatum;
		if (stammdatum.isStandardszenario()) {
			this.szenarien = STANDARD;
		} else {
			this.szenarien = szenarien;

		}

	}

	/**
	 * Create a new sheet. Caution: Caller needs to make sure that the name adheres to EXCEL conventions for sheet names. If in doubt use
	 * {@link org.apache.poi.ss.util.WorkbookUtil#createSafeSheetName(String)}
	 * 
	 * @param name
	 * @return
	 */
	private Sheet createSheet(final String name) {
		final Sheet sheet = this.wb.createSheet(name);
		sheet.protectSheet(this.password);
		return sheet;
	}

	/**
	 * Create a new row after the last existing one.
	 * 
	 * @param sheet
	 * @return
	 */
	private Row nextRow(final Sheet sheet) {
		// just sheet.getLastRowNum() doesn't work!
		return sheet.createRow(sheet.getPhysicalNumberOfRows());
	}

	/**
	 * Bold cell.
	 * 
	 * @param row
	 * @param idx
	 * @param
	 * @return
	 */
	private Cell createHeaderCell(final Row row, final int idx, final String szenarioName) {
		final Cell cell = row.createCell(idx, CellType.STRING);
		cell.setCellStyle(this.bold);
		cell.setCellValue(szenarioName);
		return cell;
	}

	/**
	 * Add bold label and non-bold value on a new row.
	 * 
	 * @param sheet
	 * @param label
	 * @param value
	 */
	private void addMetadata(final Sheet sheet, final String label, final String value) {
		final Row row = nextRow(sheet);
		createHeaderCell(row, 0, label);
		final Cell valueCell = row.createCell(1, CellType.STRING);
		valueCell.setCellValue(value);
	}

	private Calendar getStartOf(final int year) {
		final Calendar cal = Calendar.getInstance();
		cal.set(Calendar.YEAR, year);
		cal.set(Calendar.DAY_OF_YEAR, 1);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		return cal;
	}

	private boolean isLeapYear(final int year) {
		return year % 100 == 0 ? year % 400 == 0 : year % 4 == 0;
	}

	/**
	 * @param sheet
	 * @param headerCell
	 * @param string
	 */
	private void addComment(final Sheet sheet, final Row row, final Cell cell, final String text) {
		final Drawing<?> drawing = sheet.createDrawingPatriarch();
		final XSSFClientAnchor anchor = ch.createClientAnchor();
		anchor.setCol1(cell.getColumnIndex());
		anchor.setCol2(cell.getColumnIndex() + 2);
		anchor.setRow1(row.getRowNum());
		anchor.setRow2(row.getRowNum() + 1);
		final Comment comment = drawing.createCellComment(anchor);
		comment.setString(ch.createRichTextString(text));
		cell.setCellComment(comment);
	}

	/**
	 * Conditional formatting to show which columns still miss data. Renders red cross if value is bigger than 1 in non leap years or bigger than the number of entries needed for the leap day in leap
	 * years, yellow exclamation mark only for leap years and only if the value indicates that all values are present but the data for the leap day, else renders green check mark
	 * 
	 * @param length
	 *            expected correct value of the cell
	 * @param nonLeapYearCount2
	 */
	private void createConditionalFormatting(final Sheet sheet, final int startRow, final int endRow, final int startColumn, final int endColumn, final boolean isLeapYear, final int leapYearLength,
			final int nonLeapYearLength) {
		final CellRangeAddress regions = new CellRangeAddress(startRow, endRow, startColumn, endColumn);
		final SheetConditionalFormatting sheetCF = sheet.getSheetConditionalFormatting();
		final ConditionalFormattingRule rule = sheetCF.createConditionalFormattingRule(IconSet.GYR_3_SYMBOLS_CIRCLE);
		final IconMultiStateFormatting msf = rule.getMultiStateFormatting();
		msf.setReversed(true);// reverse symbol order
		msf.getThresholds()[0].setRangeType(RangeType.NUMBER);
		msf.getThresholds()[0].setValue(0.0);
		msf.getThresholds()[1].setRangeType(RangeType.NUMBER);
		msf.getThresholds()[1].setValue(isLeapYear ? 1.0 : 0.1);
		msf.getThresholds()[2].setRangeType(RangeType.NUMBER);
		msf.getThresholds()[2].setValue(isLeapYear ? (leapYearLength - nonLeapYearLength) + 1 : 1.0);
		sheetCF.addConditionalFormatting(new CellRangeAddress[] { regions }, rule);
	}

	/**
	 * Create data input sheet for one scenario. Make value cells editable.
	 * 
	 * @param sheet
	 * @param scenarioName
	 * @param startJahr
	 * @param prognoseHorizont
	 * @param timeintervals
	 */
	private void createScenarioSheet(final Sheet sheet, final String scenarioName, final int startJahr, final int prognoseHorizont, final TimeInterval timeintervals) {
		// header
		final Row header = nextRow(sheet);
		createHeaderCell(header, 0, scenarioName);

		for (int relJahr = 0; relJahr <= prognoseHorizont; relJahr++) {
			final int year = (startJahr + relJahr);
			final Cell headerCell = createHeaderCell(header, relJahr + 1, "" + year);
			if (isLeapYear(year)) {
				addComment(sheet, header, headerCell, "Schaltjahr");
			}
			// since all sheet are locked per default, explicitely allow the user to edit all
			// future cells in this column
			sheet.setDefaultColumnStyle(relJahr + 1, this.editable);
		}
		// row per timeinterval
		final Calendar cal = getStartOf(2013); // non leap year
		final Calendar calL = getStartOf(2016); // leap year
		final Calendar end = getStartOf(2014);
		final Calendar endL = getStartOf(2017);
		// we need to count the length of leap/non-leap year columns for later validation purposes
		nonLeapYearCount = leapYearCount = 0;

		while (cal.before(end) || calL.before(endL)) {
			final Row row = nextRow(sheet);
			final Cell cell = row.createCell(0, CellType.STRING);
			final String nonLeap = timeintervals.format(cal);
			final String leap = timeintervals.format(calL);
			String text;
			if (nonLeap.equals(leap)) { // same date
				text = nonLeap;
				nonLeapYearCount++;
				leapYearCount++;
			} else if (!cal.before(end)) { // non-leap year finished
				text = leap;
				leapYearCount++;
			} else {
				text = nonLeap + "/" + leap;
				nonLeapYearCount++;
				leapYearCount++;
			}
			cell.setCellValue(text);
			timeintervals.addTo(cal);
			timeintervals.addTo(calL);
		}
	}

	/**
	 * Create table that visualizes the current completion state of all data sheets.
	 * 
	 * @param cover
	 * @param stammdatum
	 * @param sheetNames
	 */
	private void createValidationTable(final Sheet cover, final Stammdatum stammdatum, final String[] sheetNames, final int horizont) {
		// table header
		final Row header = nextRow(cover);
		createHeaderCell(header, 0, "Vollständigkeit/Anzahl fehlender Werte pro Jahr");
		for (int idx = 0; idx <= horizont; idx++) {
			final int year = idx + stammdatum.getBezugsjahr();
			final Cell headerCell = createHeaderCell(header, idx + 1, "" + year);
			if (isLeapYear(year)) {
				addComment(cover, header, headerCell, "Schaltjahr");
			}
		}
		// for each scenario sheet
		for (int sheetIdx = 0; sheetIdx < szenarien.getSzenarien().size(); sheetIdx++) {
			final Row row = nextRow(cover);
			final SzenarioSetElement szenarioElement = szenarien.getSzenarien().get(sheetIdx);
			createHeaderCell(row, 0, szenarioElement.getStelle() + " " + szenarioElement.getName());
			final String sheetName = sheetNames[sheetIdx + 1];
			// createValidationCell(stammdatum, row.createCell(1, Cell.CELL_TYPE_STRING), sheetNames[0], 1, 0);
			// for each year column, count all consecutive entries
			for (int i = 0; i <= horizont; i++) {
				createValidationCell(stammdatum, row.createCell(i + 1, CellType.STRING), sheetName, i + 1, i);
			}
		}
		if (szenarien.getSzenarien().size() > 0) {
			final int crntRow = cover.getLastRowNum();
			for (int i = 0; i <= horizont; i++) {
				final int year = i + stammdatum.getBezugsjahr();
				createConditionalFormatting(
						cover,
						crntRow - szenarien.getSzenarien().size() + 1,
						crntRow,
						i + 1,
						i + 1,
						isLeapYear(year),
						this.leapYearCount,
						this.nonLeapYearCount);
			}
		}
	}

	/**
	 * Create a single validation cell on cover for one column in one data sheet
	 * 
	 * @param s
	 *            current Stammdatum
	 * @param cell
	 *            POI formula cell
	 * @param sheetName
	 * @param relativeYear
	 */
	private void createValidationCell(final Stammdatum s, final Cell cell, final String sheetName, final int column, final int relativeYear) {
		final int year = relativeYear + s.getBezugsjahr();
		final int length = (isLeapYear(year) ? this.leapYearCount : this.nonLeapYearCount);
		final String colString = CellReference.convertNumToColString(column);
		final String formula = String.format("%d-COUNTA('%s'!%s$2:%s%d)",
				length, sheetName, colString, colString, length + 1); // +1 row for the header
		cell.setCellFormula(formula);
	}

	/**
	 * Create the template contents matching the given <code>Stammdatum</code>.
	 * 
	 * @param stammdatum
	 * @param horizont
	 *            Der Prognosehorizont, der separat vom Stammdatum angegeben wird, um bei vorhandenen algebraischen Zeitreihen weniger importieren zu können
	 */
	public void createSheet(final int horizont) {
		this.valid = true;
		final Sheet cover = this.createSheet("Überblick");

		for (final StammdatumEntity entity : StammdatumEntity.values()) {
			addMetadata(cover, entity.getName(), entity.getValue(stammdatum));
		}

		final JSONObject inputDependencies = ParameterInputDependenciesUtil.getInstance().getInputDependencies(1);
		if (inputDependencies.has(stammdatum.getTyp())) {
			final String unit = ParameterInputDependenciesUtil.getInstance().getUnit(stammdatum.getTyp(), 1);
			addMetadata(cover, "Einheit", unit);

			final Map<String, BigDecimal> domain = ParameterInputDependenciesUtil.getInstance().getDomain(stammdatum.getTyp(), 1);
			String d = "";

			for (final String k : domain.keySet()) {
				d += "( " + k + " : " + domain.get(k) + " ) ";
			}
			addMetadata(cover, "Domain", d);
		} else {
			addMetadata(cover, "Einheit", "unbekannt");
			addMetadata(cover, "Domain", "unbekannt");
		}
		final String[] sheetNames;

		sheetNames = new String[szenarien.getSzenarien().size() + 1];
		for (int i = 0; i < szenarien.getSzenarien().size(); i++) {
			final SzenarioSetElement sse = szenarien.getSzenarien().get(i);
			sheetNames[i + 1] = WorkbookUtil.createSafeSheetName(sse.getStelle() + " " + sse.getName());
			// sheetNames[i + 1] = sse.getStelle() + " " + sse.getName();
			this.createScenarioSheet(this.createSheet(sheetNames[i + 1]), sse.getStelle() + " " + sse.getName(), stammdatum.getBezugsjahr(), horizont, stammdatum.getZeitintervall());
		}

		nextRow(cover); // empty row
		createValidationTable(cover, stammdatum, sheetNames, horizont);
		// increase width of first column on each sheet, because the user can't change it himself due to the workbook protection
		for (final Sheet sheet : this.wb) {
			sheet.autoSizeColumn(0);
		}
	}

	public XSSFWorkbook getWorkbook() {
		return wb;
	}

	/**
	 * Write completed XLSX file.
	 * 
	 * @param os
	 * @throws IOException
	 */
	public void writeTo(final OutputStream os) throws IOException {
		if (!valid) {
			throw new IllegalStateException("No data was specified using 'setStammdatum' yet!");
		} else {
			wb.write(os);
		}
	}

	/**
	 * @param args
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public static void main(final String[] args) throws FileNotFoundException, IOException {
		// final Stammdatum test = new Stammdatum("Elektrisches Lastprofil", "par_A_B_C", "muster@mann.com", "muster@mann.com", TimeInterval.QUARTERHOUR, 2016, 10, new String[] { "LEME COL",
		// "LEME ECON"});
		final Stammdatum sd_therm = new Stammdatum();
		sd_therm.setBezugsjahr(2016);
		sd_therm.setPrognoseHorizont(2);
		sd_therm.setZeitintervall(TimeInterval.DAY);
		sd_therm.setName("Thermisches Lastprofil");
		// sd_therm.setName("Elektrisches Lastprofil2");
		sd_therm.setTyp("par_L_DS_G");
		// sd_therm.setTyp("par_L_DS_E");

		final List<Integer> szenarien = new LinkedList<>();
		szenarien.add(1);
		szenarien.add(2);

		final SzenarioSet ss = new SzenarioSet();
		ss.setJahr(2016);
		final SzenarioSetElement sseA = new SzenarioSetElement();
		sseA.setStelle(1);
		sseA.setName("LEME COL");
		ss.getSzenarien().add(sseA);

		final SzenarioSetElement sseB = new SzenarioSetElement();
		sseB.setStelle(2);
		sseB.setName("LEME ECON");
		ss.getSzenarien().add(sseB);

		final ImportTemplateGenerator gen = new ImportTemplateGenerator("irpsim", sd_therm, ss);
		final StopWatch watch = new StopWatch();
		watch.start();
		gen.createSheet(sd_therm.getPrognoseHorizont());
		watch.split();
		LOG.debug("Generating in-memory workshoot took {}ms.", watch.getSplitTime());
		try (OutputStream os = new FileOutputStream("test.xlsx")) {
			gen.writeTo(os);
		}
		watch.stop();
		LOG.debug("Overall generation time: {}ms", watch.getTime());
	}

}
