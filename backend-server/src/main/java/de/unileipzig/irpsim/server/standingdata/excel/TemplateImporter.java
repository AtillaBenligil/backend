package de.unileipzig.irpsim.server.standingdata.excel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.joda.time.DurationFieldType;
import org.joda.time.MutableDateTime;

import de.unileipzig.irpsim.core.simulation.data.TimeInterval;
import de.unileipzig.irpsim.core.standingdata.AddData;
import de.unileipzig.irpsim.core.standingdata.StammdatumEntity;
import de.unileipzig.irpsim.core.standingdata.SzenarioSet;
import de.unileipzig.irpsim.core.standingdata.SzenarioSetElement;
import de.unileipzig.irpsim.core.standingdata.TimeseriesValue;
import de.unileipzig.irpsim.core.standingdata.data.Stammdatum;
import de.unileipzig.irpsim.core.utils.ParameterInputDependenciesUtil;

public class TemplateImporter {

	private static final Logger LOG = LogManager.getLogger(TemplateImporter.class);

	private final Workbook workbook;
	private Stammdatum stammdatum;
	private String wrongDatasets = "";
	private final List<AddData> addDataList = new ArrayList<>();

	public TemplateImporter(final Workbook wb) {
		this.workbook = wb;
		this.stammdatum = fetchStammdatum(wb);
	}

	public void init(final Stammdatum stammdatum, final SzenarioSet szenarioSet) {
		this.stammdatum = stammdatum;
		if (!stammdatum.isStandardszenario()) {
			for (int shtIdx = 1; shtIdx < workbook.getNumberOfSheets(); shtIdx++) {
				final Sheet sheet = workbook.getSheetAt(shtIdx);
				final Row row = sheet.getRow(0);
				final String scenarioName = row.getCell(0).getStringCellValue();
				final int stelle = Integer.parseInt(scenarioName.split(" ")[0]);

				if (szenarioSet.hasStelle(stelle)) {
					for (int colIdx = 1; colIdx <= stammdatum.getPrognoseHorizont() + 1; colIdx++) {
						// System.out.println(scenarioName + " " + row.getCell(colIdx).getStringCellValue());
						final AddData addData = fetchAddData(sheet, colIdx, stelle);
						if (addData != null && addData.getValues().length != 0) {
							addDataList.add(addData);
							LOG.debug(addData.getSzenario() + " " + addData.getJahr() + " " + addData.getValues().length);
						}
					}
				}
			}
		} else {
			if (workbook.getNumberOfSheets() > 2) {
				throw new RuntimeException("Exceldatensätze mit Standardszenario dürfen nur 2 Datenblätter haben.");
			}

			final Sheet sheet = workbook.getSheetAt(1);
			final Row row = sheet.getRow(0);
			final String scenarioName = row.getCell(0).getStringCellValue();
			final int stelle = Integer.parseInt(scenarioName.split(" ")[0]);
			if (stelle != 0) {
				throw new RuntimeException("Stelle des Standardszenario muss 0 sein.");
			}

			for (final SzenarioSetElement element : szenarioSet.getSzenarien()) {
				for (int colIdx = 1; colIdx <= stammdatum.getPrognoseHorizont() + 1; colIdx++) {
					// System.out.println(scenarioName + " " + row.getCell(colIdx).getStringCellValue());
					final AddData addData = fetchAddData(sheet, colIdx, element.getStelle());
					if (addData != null && addData.getValues().length != 0) {
						addDataList.add(addData);
						LOG.debug(addData.getSzenario() + " " + addData.getJahr() + " " + addData.getValues().length);
					}
				}
			}
		}

		for (final AddData ad : addDataList) {
			final boolean isLeafYear = ad.isSchaltjahr();
			if (ad.getValues().length != stammdatum.getZeitintervall().getCountInYear() && (!isLeafYear || ad.getValues().length != stammdatum.getZeitintervall().getCountInYear(isLeafYear))) {
				wrongDatasets += "Daten in " + ad.getSzenario() + " (" + ad.getJahr() + ") haben falsche Länge, " +
						"erwartet: " + stammdatum.getZeitintervall().getCountInYear() + " vorliegend: " + ad.getValues().length;
			}
		}
	}

	/**
	 * Überprüft die gelesenen Zeitreihen anhand der für den Parameter definierten Domain-Regeln.
	 * 
	 * @return Liste mit Fehlermeldungen.
	 */
	public List<String> checkForDomainViolations() {

		final List<String> violations = new ArrayList<>();

		if (ParameterInputDependenciesUtil.getInstance().getInputDependencies(1).has(stammdatum.getTyp())) {
			final Map<String, BigDecimal> domain = ParameterInputDependenciesUtil.getInstance().getDomain(stammdatum.getTyp(), 1);

			for (final AddData a : addDataList) {
				for (final TimeseriesValue v : a.getValues()) {
					for (final String rule : domain.keySet()) {
						boolean passing = true;
						switch (rule) {
						case ">":
							if (!(v.getValue() > domain.get(rule).doubleValue())) {
								passing = false;
							}
							break;
						case ">=":
							if (!(v.getValue() >= domain.get(rule).doubleValue())) {
								passing = false;
							}
							break;
						case "<":
							if (!(v.getValue() < domain.get(rule).doubleValue())) {
								passing = false;
							}
							break;
						case "<=":
							if (!(v.getValue() <= domain.get(rule).doubleValue())) {
								passing = false;
							}
							break;
						}
						if (!passing) {
							violations.add(v.getValue() + " in (" + a.getJahr() +
									", " + a.getSzenario() + ") verletzt Domain-Regel " + rule + " " + domain.get(rule).doubleValue() + ";");
						}
					}
				}
			}
		}
		return violations;
	}

	public String getWrongDatasets() {
		return wrongDatasets;
	}

	public AddData[] getAddData() {
		return addDataList.toArray(new AddData[addDataList.size()]);
	}

	// Copy from ImportTemplateGenerator
	private MutableDateTime getStartOf(final int year) {
		final MutableDateTime dt = new MutableDateTime(year, 1, 1, 0, 0, 0, 0);
		return dt;
	}

	public static Stammdatum fetchStammdatum(Workbook workbook) {
		final Stammdatum result = new Stammdatum();
		final Sheet sheet = workbook.getSheetAt(0);
		for (final StammdatumEntity entity : StammdatumEntity.values()) {
			final Row row = sheet.getRow(entity.getIndex() - 1);
			final Cell cell = row.getCell(1);
			entity.setValue(result, cell.getStringCellValue());
		}
		return result;
	}

	public AddData fetchAddData(final Sheet sheet, final int colIdx, final int scenarioStelle) {
		final AddData addData = new AddData();
		addData.setSzenario(scenarioStelle);
		addData.setJahr(stammdatum.getBezugsjahr() - 1 + colIdx);

		int rowIdx = 1;
		final List<Double> values = new LinkedList<>();
		while (rowIdx <= sheet.getLastRowNum()) {
			final Row row = sheet.getRow(rowIdx);
			if (row == null) {
				break;
			}
			final Cell cell = row.getCell(colIdx);
			if (cell == null || (cell.getCellType() != CellType.NUMERIC && cell.getCellType() != CellType.FORMULA)) {
				break;
			}
			values.add(cell.getNumericCellValue());
			rowIdx++;
		}

		final List<TimeseriesValue> tsvList = new ArrayList<>();
		final MutableDateTime cal = getStartOf(addData.getJahr());
		// final Calendar calNext = this.getStartOf(addData.getJahr() + 1); // TODO Reicht es nicht, alle Zeilen bis unten durchzulaufen?
		for (final Double value : values) {
			final TimeseriesValue timeseriesValue = new TimeseriesValue(cal.getMillis(), value);
			tsvList.add(timeseriesValue);
			if (addData.isSchaltjahr() && values.size() == stammdatum.getZeitintervall().getCountInYear(false) && cal.getDayOfMonth() == 29 && cal.getMonthOfYear() == 2) {
				if (stammdatum.getZeitintervall().equals(TimeInterval.DAY) || stammdatum.getZeitintervall().equals(TimeInterval.HOUR)
						|| stammdatum.getZeitintervall().equals(TimeInterval.QUARTERHOUR)) {
					cal.add(DurationFieldType.days(), 1);
				}
			} else {
				stammdatum.getZeitintervall().addTo(cal);
			}
		}

		addData.setValues(tsvList.toArray(new TimeseriesValue[tsvList.size()]));

		return addData;
	}

	/**
	 * @param args
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @throws InvalidFormatException
	 */
	public static void main(final String[] args) throws FileNotFoundException, IOException, InvalidFormatException {
		final File inFile = new File("/home/reichelt/Downloads/2015 - asdf - zum L schen.xlsx");
		final InputStream is = new FileInputStream(inFile);
		final OPCPackage pkg = OPCPackage.open(is);
		final XSSFWorkbook wb = new XSSFWorkbook(pkg);
		final Stammdatum sd = new Stammdatum("Stromlast", "par_A_B_C", TimeInterval.MONTH, 2015, 1, new String[] { "LEME A", "LEME B" });

		final SzenarioSet sse = new SzenarioSet();

		final TemplateImporter importer = new TemplateImporter(wb);
		importer.init(sd, sse);
		importer.getAddData();

	}

}
