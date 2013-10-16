package contMensili;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.gui.Roi;
import ij.io.OpenDialog;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.util.Tools;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import utils.AboutBox;
import utils.ArrayUtils;
import utils.ButtonMessages;
import utils.ImageUtils;
import utils.InputOutput;
import utils.MyMsg;
import utils.MyConst;
import utils.MyLog;
import utils.MyStackUtils;
import utils.ReadDicom;
import utils.ReportStandardInfo;
import utils.TableCode;
import utils.TableSequence;
import utils.UtilAyv;

/*
 * Copyright (C) 2007 Alberto Duina, SPEDALI CIVILI DI BRESCIA, Brescia ITALY
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

/**
 * Analizza SPESSORE FETTA effettua 4 profili, non necessariamente orizzontali ,
 * mediati su 11 linee e su questi calcola FWHM e posizione del picco. Lavora
 * sia su singola immagine che su stack
 * 
 * Per salvare i dati in formato xls necessita di Excel_Writer.jar nella
 * directory plugins
 * 
 * ____29jan07 v 3.00 i dati di output sugli spessori sono ora in mm (matr/fov)
 * 
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 * 
 */

public class p6rmn_ implements PlugIn, Measurements {

	static final int ABORT = 1;

	public static String VERSION = "p6_rmn_v5.10_10feb11_";

	private static String TYPE1 = " >> CONTROLLO THICKNESS______________";

	private static String TYPE2 = " >> CONTROLLO THICKNESS MULTISLICE___";

	private static String fileDir = "";

	// ---------------------------"01234567890123456789012345678901234567890"

	public void run(String args) {
		// for our phantom the nominal fwhm for thickness 2 mm is 10.3
		// for our phantom the nominal fwhm for thickness 5 mm is 25.7
		// peak position is the maximum profile position
		// manual selection of unaccettable slices (border or center (cross
		// slabs) slices
		if (IJ.versionLessThan("1.43k"))
			return;
		//
		// nota bene: le seguenti istruzioni devono essere all'inizio, in questo
		// modo il messaggio viene emesso, altrimenti si ha una eccezione
		//
		try {
			Class.forName("utils.IW2AYV");
		} catch (ClassNotFoundException e) {
			IJ.error("ATTENZIONE, manca il file iw2ayv_xxx.jar");
			return;
		}

		fileDir = Prefs.get("prefer.string1", "none");

		int nTokens = new StringTokenizer(args, "#").countTokens();
		if (nTokens == 0) {
			manualMenu(0, "");
		} else {
			autoMenu(args);
		}
		return;
	}

	public int manualMenu(int preset, String testDirectory) {
		boolean retry = false;
		boolean step = false;
		do {
			int userSelection1 = UtilAyv.userSelectionManual(VERSION, TYPE2);
			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				retry = false;
				return 0;
			case 2:
				// new AboutBox().about("Controllo Thickness", this.getClass());
				new AboutBox().about("Controllo Thickness",
						MyVersion.CURRENT_VERSION);
				retry = true;
				break;
			case 3:
				retry = false;
				selfTestMenu();
				break;
			case 4:
				step = true;
				retry = false;
				// dont put break here!
			case 5:
				String[] path = manualImageSelection();
				if (path == null)
					return 5;
				boolean autoCalled = false;
				boolean verbose = false;
				boolean test = false;
				ImagePlus imp0 = UtilAyv.openImageNoDisplay(path[0], true);
				double[] oldPosition = readReferences(imp0);
				new p6rmn_().wrapThickness(path, "0", oldPosition, autoCalled,
						step, verbose, test);
				retry = false;
			}
		} while (retry);
		new AboutBox().close();
		UtilAyv.afterWork();
		return 0;
	}

	/**
	 * Auto menu invoked from Sequenze_
	 * 
	 * @param autoArgs
	 * @return
	 */
	public int autoMenu(String autoArgs) {
		MyLog.appendLog(fileDir + "MyLog.txt", "p6 riceve " + autoArgs);

		// the autoArgs are passed from Sequenze_
		// possibilities:
		// 1 token -1 = silentAutoTest
		// 2 tokens auto
		// 4 tokens auto
		int nTokens = new StringTokenizer(autoArgs, "#").countTokens();
		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);
		if (vetRiga[0] == -1) {
			IJ.log("selfTestSilent.p6rmn_");
			selfTestSilent();
			return 0;
		}

		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir
				+ MyConst.SEQUENZE_FILE);

		boolean retry = false;
		boolean step = false;
		int userSelection1 = 0;
		do {
			if (nTokens == 1)
				// userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE1);
				userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE1,
						TableSequence.getCode(iw2ayvTable, vetRiga[0]),
						TableSequence.getCoil(iw2ayvTable, vetRiga[0]),
						vetRiga[0] + 1, TableSequence.getLength(iw2ayvTable));

			else
				// userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE2);
				userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE2,
						TableSequence.getCode(iw2ayvTable, vetRiga[0]),
						TableSequence.getCoil(iw2ayvTable, vetRiga[0]),
						vetRiga[0] + 1, TableSequence.getLength(iw2ayvTable));

			step = false;
			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				return 0;
			case 2:
				// new AboutBox().about("Controllo Thickness", this.getClass());
				new AboutBox().about("Controllo Thickness",
						MyVersion.CURRENT_VERSION);
				retry = true;
				break;
			case 3:
				step = true;
				retry = false;
				// dont put break here!
			case 4:
				retry = false;
				boolean verbose = true;
				boolean test = false;
				boolean autoCalled = true;
				String[] path = loadPath(autoArgs);
				ImagePlus imp0 = UtilAyv.openImageNoDisplay(path[0], true);
				double[] vetRefPosition = readReferences(imp0);
				new p6rmn_().wrapThickness(path, autoArgs, vetRefPosition,
						autoCalled, step, verbose, test);
				UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable);

				break;
			}
		} while (retry);
		new AboutBox().close();
		UtilAyv.afterWork();
		return 0;
	}

	public String[] manualImageSelection() {
		boolean endsel = false;
		List<String> listPath = new ArrayList<String>();
		do {
			OpenDialog od1 = new OpenDialog("SELEZIONARE IMMAGINE...", "");
			String directory1 = od1.getDirectory();
			String name1 = od1.getFileName();
			if (name1 == null)
				return null;
			listPath.add(directory1 + name1);
			int userSelection = ButtonMessages.ModelessMsg("Finito ?",
					"SELEZIONA ALTRA IMMAGINE", "FINE SELEZIONE");
			if (userSelection == 1)
				endsel = true;
		} while (!endsel);
		String[] path = ArrayUtils.arrayListToArrayString(listPath);
		return path;
	}

	/**
	 * Self test execution menu
	 */
	public void selfTestMenu() {
		if (new InputOutput().checkJar(MyConst.TEST_FILE)) {
			int userSelection2 = UtilAyv.siemensGe();
			switch (userSelection2) {
			case 1: {
				// GE
				String[] list = { "HT5A2_testP6" };
				String[] path1 = new InputOutput().findListTestImages2(
						MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
				double[] vetRefPosition = MyConst.P6_REFERENCE_LINE_GE;
				// String[] path={"./Test2/BT2A_testP6"};
				String autoArgs = "0";
				boolean autoCalled = false;
				boolean step = false;
				boolean verbose = false;
				boolean test = true;
				ResultsTable rt = new p6rmn_().mainThickness(path1, autoArgs,
						vetRefPosition, autoCalled, step, verbose, test);
				double[] vetResults = UtilAyv.vectorizeResults(rt);
				boolean ok = UtilAyv.verifyResults1(vetResults, referenceGe(),
						MyConst.P6_vetName);
				if (ok)
					MyMsg.msgTestPassed();
				else
					MyMsg.msgTestFault();
				UtilAyv.afterWork();

				break;
			}
			case 2:
				// Siemens
				String[] list = { "BT2A_testP6" };
				String[] path1 = new InputOutput().findListTestImages2(
						MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
				double[] vetRefPosition = MyConst.P6_REFERENCE_LINE_SIEMENS;
				// String[] path={"./Test2/BT2A_testP6"};
				String autoArgs = "0";
				boolean autoCalled = false;
				boolean step = false;
				boolean verbose = false;
				boolean test = true;

				ResultsTable rt = new p6rmn_().mainThickness(path1, autoArgs,
						vetRefPosition, autoCalled, step, verbose, test);

				double[] vetResults = UtilAyv.vectorizeResults(rt);
				boolean ok = UtilAyv.verifyResults1(vetResults,
						referenceSiemens(), MyConst.P6_vetName);
				if (ok)
					MyMsg.msgTestPassed();
				else
					MyMsg.msgTestFault();
				UtilAyv.afterWork();
				break;
			}
		} else {
			UtilAyv.noTest2();
		}
		return;
	}

	/**
	 * Automatic silent self test
	 */
	public void selfTestSilent() {
		// Siemens
		String[] list = { "BT2A_testP6" };
		String[] path1 = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

		double[] vetRefPosition = MyConst.P6_REFERENCE_LINE_SIEMENS;
		String autoArgs = "-1";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = false;
		boolean test = true;

		ResultsTable rt = new p6rmn_().mainThickness(path1, autoArgs,
				vetRefPosition, autoCalled, step, verbose, test);

		double[] vetResults = UtilAyv.vectorizeResults(rt);
		boolean ok = UtilAyv.verifyResults1(vetResults, referenceSiemens(),
				MyConst.P6_vetName);
		if (ok)
			IJ.log("Il test di p6rmn_ THICKNESS è stato SUPERATO");
		else
			IJ.log("Il test di p6rmn_ THICKNESS evidenzia degli ERRORI");
	}

	public String[] loadPath(String autoArgs) {
		String fileDir = Prefs.get("prefer.string1", "./test2/");
		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir
				+ MyConst.SEQUENZE_FILE);
		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);
		String[] path = new String[vetRiga.length];
		for (int i1 = 0; i1 < vetRiga.length; i1++) {
			path[i1] = TableSequence.getPath(iw2ayvTable, vetRiga[i1]);
		}
		return path;
	}

	public double[] readReferences(ImagePlus imp1) {
		int rows = imp1.getHeight();
		float dimPixel = ReadDicom
				.readFloat(ReadDicom.readSubstring(ReadDicom
						.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING),
						1));

		double[] vetReference = new double[4];
		vetReference[0] = ReadDicom.readDouble(Prefs.get("prefer.p6rmnAx", ""
				+ 50.0 / dimPixel));
		vetReference[1] = ReadDicom.readDouble(Prefs.get("prefer.p6rmnAy", ""
				+ 60.0 / dimPixel));
		vetReference[2] = ReadDicom.readDouble(Prefs.get("prefer.p6rmnBx", ""
				+ 50.0 / dimPixel));
		vetReference[3] = ReadDicom.readDouble(Prefs.get("prefer.p6rmnBy", ""
				+ 215.0 / dimPixel));
		if (interdiction(vetReference, rows)) {
			vetReference[0] = 50.0 / dimPixel;
			vetReference[1] = 60.0 / dimPixel;
			vetReference[2] = 50.0 / dimPixel;
			vetReference[3] = 215.0 / dimPixel;
		}
		return vetReference;
	}

	public void saveReferences(ImagePlus imp1) {
		Line line1 = (Line) imp1.getRoi();
		Prefs.set("prefer.p6rmnAx", "" + line1.x1);
		Prefs.set("prefer.p6rmnAy", "" + line1.y1);
		Prefs.set("prefer.p6rmnBx", "" + line1.x2);
		Prefs.set("prefer.p6rmnBy", "" + line1.y2);
	}

	public void wrapThickness(String[] path, String autoArgs,
			double[] vetRefPosition, boolean autoCalled, boolean step,
			boolean verbose, boolean test) {
		boolean accetta = false;

		do {
			ResultsTable rt = mainThickness(path, autoArgs, vetRefPosition,
					autoCalled, step, verbose, test);
			rt.show("Results");

			if (autoCalled && !test) {
				accetta = MyMsg.accettaMenu();
			} else {
				if (!test) {
					accetta = MyMsg.msgStandalone();
				} else
					accetta = test;
			}
			// MyLog.here();

		} while (!accetta);
	}

	@SuppressWarnings("deprecation")
	public ResultsTable mainThickness(String[] path, String autoArgs,
			double[] vetRefPosition, boolean autoCalled, boolean step,
			boolean verbose, boolean test) {

		int nFrames = 0;
		double vetProfile[] = { 0, 0, 0, 0 }; // [xStart,yStart,xEnd,yEnd]
		double lato = 0;

		int len = path.length;
		String[] slicePos2 = new String[len];
		double[] fwhmSlice1 = new double[len];
		double[] peakPositionSlice1 = new double[len];
		double[] fwhmSlice2 = new double[len];
		double[] peakPositionSlice2 = new double[len];
		double[] fwhmCuneo3 = new double[len];
		double[] peakPositionCuneo3 = new double[len];
		double[] fwhmCuneo4 = new double[len];
		double[] peakPositionCuneo4 = new double[len];
		int[] vetAccettab = new int[len];
		double[] vetS1CorSlab = new double[len];
		double[] vetS2CorSlab = new double[len];
		double[] vetS1CorCuneo = new double[len];
		double[] vetS2CorCuneo = new double[len];
		double[] vetErrSpessSlab = new double[len];
		double[] vetErrSpessCuneo = new double[len];
		double[] vetAccurSpessSlab = new double[len];
		double[] vetAccurSpessCuneo = new double[len];
		ResultsTable rt = null;

		ImagePlus impStack = stackBuilder(path, true);

		impStack.setSliceWithoutUpdate(1);

		if (verbose)
			UtilAyv.showImageMaximized(impStack);
		float dimPixel = ReadDicom.readFloat(ReadDicom.readSubstring(ReadDicom
				.readDicomParameter(impStack, MyConst.DICOM_PIXEL_SPACING), 1));
		impStack.setRoi(new Line((int) vetRefPosition[0],
				(int) vetRefPosition[1], (int) vetRefPosition[2],
				(int) vetRefPosition[3]));
		impStack.updateAndDraw();
		if (!test)
			msgSquare();
		//
		// legge la posizione finale del segmento dopo il posizionamento
		//
		Roi roi = impStack.getRoi();
		Line line1 = (Line) roi;
		vetRefPosition[0] = line1.x1;
		vetRefPosition[1] = line1.y1;
		vetRefPosition[2] = line1.x2;
		vetRefPosition[3] = line1.y2;
		lato = line1.getRawLength();

		if (!test)
			saveReferences(impStack);
		if (step)
			msgSquareCoordinates(vetRefPosition);

		//
		// elaborazione immagini dello stack
		//

		nFrames = impStack.getStackSize();

		double thick = ReadDicom.readDouble(ReadDicom.readDicomParameter(
				impStack, MyConst.DICOM_SLICE_THICKNESS));
		double spacing = ReadDicom.readDouble(ReadDicom.readDicomParameter(
				impStack, MyConst.DICOM_SPACING_BETWEEN_SLICES));

		for (int w1 = 0; w1 < nFrames; w1++) {

			ImagePlus imp3 = MyStackUtils.imageFromStack(impStack, w1 + 1);

			String pos2 = ReadDicom.readDicomParameter(imp3,
					MyConst.DICOM_IMAGE_POSITION);
			slicePos2[w1] = ReadDicom.readSubstring(pos2, 3);

			if (verbose)
				UtilAyv.showImageMaximized(imp3);
			if (nFrames > 1) {
				int userSelection3 = msgAccept();
				vetAccettab[w1] = userSelection3;
			}

			if (!step)
				imp3.hide();
			// Phantom positioning: the phantom MUST have the slabs in high
			// position and the wedges in lower position
			//
			// First slab analysis
			//
			// refPosition [startX = 13, startY=65, endX = 147, endY=65,
			// radius=10]

			if (step)
				msgProfile();

			int ra1 = (int) (lato / 12.0);
			vetProfile[0] = lato / 13.0;
			vetProfile[1] = lato / 5.0;
			vetProfile[2] = lato - lato / 13.0;
			vetProfile[3] = lato / 5.0;

			boolean isSlab = true;
			boolean invertErf = false;
			boolean putLabelSx = false;
			if (imp3.isVisible()) {
				imp3.getWindow().toFront();
			}

			double[] dsd1 = analProf(imp3, vetRefPosition, vetProfile, ra1,
					isSlab, invertErf, step, putLabelSx, dimPixel);

			fwhmSlice1[w1] = dsd1[0];
			peakPositionSlice1[w1] = dsd1[1];

			if (imp3.isVisible())
				imp3.getWindow().toFront();
			//
			// Second slab analysis
			//
			if (step)
				msgProfile();
			// refPosition [startX = 13, startY=65, endX = 147, endY=65,
			// radius=10]

			ra1 = (int) (lato / 12.0);
			vetProfile[0] = lato / 13.0;
			vetProfile[1] = lato * 2.0 / 5.0;
			vetProfile[2] = lato - lato / 13.0;
			vetProfile[3] = lato * 2.0 / 5.0;

			isSlab = true;
			invertErf = false;
			putLabelSx = true;
			if (step) {
				imp3.getWindow().toFront();
			}
			double[] dsd2 = analProf(imp3, vetRefPosition, vetProfile, ra1,
					isSlab, invertErf, step, putLabelSx, dimPixel);
			fwhmSlice2[w1] = dsd2[0];
			peakPositionSlice2[w1] = dsd2[1];

			if (imp3.isVisible())
				imp3.getWindow().toFront();
			double[] spessCor1 = spessStrato(dsd1[0], dsd2[0], (double) thick,
					dimPixel);

			vetS1CorSlab[w1] = spessCor1[0];
			vetS2CorSlab[w1] = spessCor1[1];
			vetErrSpessSlab[w1] = spessCor1[2];
			vetAccurSpessSlab[w1] = spessCor1[3];
			//
			// First wedge analysis
			//
			if (step)
				msgProfile();
			// refPosition [startX = 13, startY=98, endX = 147, endY=98,
			// radius=10]

			ra1 = (int) (lato / 12.0);
			vetProfile[0] = lato / 13.0;
			vetProfile[1] = lato * 3.0 / 5.0;
			vetProfile[2] = lato - lato / 13.0;
			vetProfile[3] = lato * 3.0 / 5.0;

			isSlab = false;
			invertErf = true;
			putLabelSx = false;
			if (imp3.isVisible()) {
				imp3.getWindow().toFront();
			}

			double[] dsd3 = analProf(imp3, vetRefPosition, vetProfile, ra1,
					isSlab, invertErf, step, putLabelSx, dimPixel);

			fwhmCuneo3[w1] = dsd3[0];
			peakPositionCuneo3[w1] = dsd3[1];

			if (imp3.isVisible())
				imp3.getWindow().toFront();
			//
			// Second wedge analysis
			//
			if (step)
				msgProfile();
			// refPosition [startX = 13, startY=133, endX = 147, endY=133,
			// radius=10]

			ra1 = (int) (lato / 12.0);
			vetProfile[0] = lato / 13.0;
			vetProfile[1] = lato * 4.0 / 5.0;
			vetProfile[2] = lato - lato / 13.0;
			vetProfile[3] = lato * 4.0 / 5.0;

			isSlab = false;
			invertErf = false;
			putLabelSx = true;
			if (step) {
				imp3.getWindow().toFront();
			}
			double[] dsd4 = analProf(imp3, vetRefPosition, vetProfile, ra1,
					isSlab, invertErf, step, putLabelSx, dimPixel);
			fwhmCuneo4[w1] = dsd4[0];
			peakPositionCuneo4[w1] = dsd4[1];

			if (imp3.isVisible())
				imp3.getWindow().toFront();
			double[] spessCor2 = spessStrato(dsd3[0], dsd4[0], (double) thick,
					dimPixel);
			vetS1CorCuneo[w1] = spessCor2[0];
			vetS2CorCuneo[w1] = spessCor2[1];
			vetErrSpessCuneo[w1] = spessCor2[2];
			vetAccurSpessCuneo[w1] = spessCor2[3];

		}

		//
		// Salvataggio dei risultati nella results table
		//

		// String[][] tabCodici = new InputOutput().readFile1(MyConst.CODE_FILE,
		// MyConst.TOKENS4);

		String[][] tabCodici = TableCode.loadTableCSV(MyConst.CODE_FILE);

		String[] info1 = ReportStandardInfo.getSimpleStandardInfo(path[0],
				impStack, tabCodici, VERSION, autoCalled);
		rt = ReportStandardInfo.putSimpleStandardInfoRT(info1);

		int col = 0;

		String t1 = "TESTO          ";
		rt.setHeading(++col, t1);
		for (int j1 = 0; j1 < nFrames; j1++)
			rt.setHeading(++col, "SLICE" + j1);
		rt.setHeading(++col, "seg_ax");
		rt.setHeading(++col, "seg_ay");
		rt.setHeading(++col, "seg_bx");
		rt.setHeading(++col, "seg_by");

		rt.addLabel(t1, " slicePos");
		for (int j1 = 0; j1 < nFrames; j1++)
			rt.addValue(j1 + 2, UtilAyv.convertToDouble(slicePos2[j1]));
		rt.addValue(nFrames + 2, vetRefPosition[0]);
		rt.addValue(nFrames + 3, vetRefPosition[1]);
		rt.addValue(nFrames + 4, vetRefPosition[2]);
		rt.addValue(nFrames + 5, vetRefPosition[3]);

		rt.incrementCounter();
		rt.addLabel(t1, "fwhm_slab1");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(j1 + 2, fwhmSlice1[j1]);
		}

		rt.incrementCounter();
		rt.addLabel(t1, "peak_slab1");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(j1 + 2, peakPositionSlice1[j1]);
		}

		rt.incrementCounter();
		rt.addLabel(t1, "fwhm_slab2");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(j1 + 2, fwhmSlice2[j1]);
		}

		rt.incrementCounter();
		rt.addLabel(t1, "peak_slab2");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(j1 + 2, peakPositionSlice2[j1]);
		}

		rt.incrementCounter();
		rt.addLabel(t1, "fwhm_cuneo3");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(j1 + 2, fwhmCuneo3[j1]);
		}

		rt.incrementCounter();
		rt.addLabel(t1, "peak_cuneo3");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(j1 + 2, peakPositionCuneo3[j1]);
		}

		rt.incrementCounter();
		rt.addLabel(t1, "fwhm_cuneo4");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(j1 + 2, fwhmCuneo4[j1]);
		}

		rt.incrementCounter();
		rt.addLabel(t1, "peak_cuneo4");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(j1 + 2, peakPositionCuneo4[j1]);
		}

		rt.incrementCounter();
		rt.addLabel(t1, "S1CorSlab");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(j1 + 2, vetS1CorSlab[j1]);
		}

		rt.incrementCounter();
		rt.addLabel(t1, "S2CorSlab");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(j1 + 2, vetS2CorSlab[j1]);
		}

		rt.incrementCounter();
		rt.addLabel(t1, "ErrSperSlab");
		for (int j1 = 0; j1 < nFrames; j1++)
			rt.addValue(j1 + 2, vetErrSpessSlab[j1]);

		rt.incrementCounter();
		rt.addLabel(t1, "AccurSpesSlab");
		for (int j1 = 0; j1 < nFrames; j1++)
			rt.addValue(j1 + 2, vetAccurSpessSlab[j1]);

		rt.incrementCounter();
		rt.addLabel(t1, "S1CorCuneo");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(j1 + 2, vetS1CorCuneo[j1]);
		}

		rt.incrementCounter();
		rt.addLabel(t1, "S2CorCuneo");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(j1 + 2, vetS2CorCuneo[j1]);
		}

		rt.incrementCounter();
		rt.addLabel(t1, "ErrSperCuneo");
		for (int j1 = 0; j1 < nFrames; j1++)
			rt.addValue(j1 + 2, vetErrSpessCuneo[j1]);

		rt.incrementCounter();
		rt.addLabel(t1, "AccurSpesCuneo");
		for (int j1 = 0; j1 < nFrames; j1++)
			rt.addValue(j1 + 2, vetAccurSpessCuneo[j1]);

		rt.incrementCounter();
		rt.addLabel(t1, "Accettab");
		for (int j1 = 0; j1 < nFrames; j1++)
			rt.addValue(j1 + 2, vetAccettab[j1]);

		rt.incrementCounter();
		rt.addLabel(t1, "DimPix");
		rt.addValue(2, dimPixel);

		rt.incrementCounter();
		rt.addLabel(t1, "Thick");
		rt.addValue(2, thick);

		rt.incrementCounter();
		rt.addLabel(t1, "Spacing");
		rt.addValue(2, spacing);

		return rt;
	}

	/**
	 * Costruisce uno stack a partire dal path delle immagini e lo riordina
	 * secondo la posizione delle fette.
	 * 
	 * @param path
	 *            vettore contenente il path delle immagini
	 * @return ImagePlus contenente lo stack generato
	 */
	public ImagePlus stackBuilder(String[] path, boolean verbose) {

		if ((path == null) || (path.length == 0)) {
			if (verbose)
				IJ.log("stackBuilder path==null or 0 length.");
			return null;
		}
		ImagePlus imp0 = UtilAyv.openImageNoDisplay(path[0], true);
		int rows = imp0.getHeight();
		int columns = imp0.getWidth();
		ImageStack newStack = new ImageStack(rows, columns);
		String[] slicePos2 = new String[path.length];

		for (int w1 = 0; w1 < path.length; w1++) {
			imp0 = UtilAyv.openImageNoDisplay(path[w1], true);
			String dicomPosition = ReadDicom.readDicomParameter(imp0,
					MyConst.DICOM_IMAGE_POSITION);
			slicePos2[w1] = ReadDicom.readSubstring(dicomPosition, 3);
		}

		String[] pathSortato = bubbleSortPath(path, slicePos2);

		for (int w1 = 0; w1 < pathSortato.length; w1++) {
			ImagePlus imp1 = UtilAyv.openImageNoDisplay(path[w1], true);
			ImageProcessor ip1 = imp1.getProcessor();
			if (w1 == 0)
				newStack.update(ip1);
			String sliceInfo1 = imp1.getTitle();
			String sliceInfo2 = (String) imp1.getProperty("Info");
			// aggiungo i dati header alle singole immagini dello stack
			if (sliceInfo2 != null)
				sliceInfo1 += "\n" + sliceInfo2;
			newStack.addSlice(sliceInfo1, ip1);
		}
		ImagePlus newImpStack = new ImagePlus("newSTACK", newStack);
		if (pathSortato.length == 1) {
			String sliceInfo3 = imp0.getTitle();
			sliceInfo3 += "\n" + (String) imp0.getProperty("Info");
			newImpStack.setProperty("Info", sliceInfo3);
		}
		newImpStack.getProcessor().resetMinAndMax();
		return newImpStack;
	}

	/**
	 * 
	 * @param path
	 * @param slicePosition
	 * @return
	 */
	public String[] bubbleSortPath(String[] path, String[] slicePosition) {

		if (path == null)
			return null;
		if (slicePosition == null)
			return null;
		if (!(path.length == slicePosition.length))
			return null;
		if (path.length < 2) {
			return path;
		}
		//
		// bubblesort
		//
		String[] sortedPath = new String[path.length];
		sortedPath = path;

		for (int i1 = 0; i1 < path.length; i1++) {
			for (int i2 = 0; i2 < path.length - 1 - i1; i2++) {
				double position1 = ReadDicom.readDouble(slicePosition[i2]);
				double position2 = ReadDicom.readDouble(slicePosition[i2 + 1]);
				if (position1 > position2) {
					String positionSwap = slicePosition[i2];
					slicePosition[i2] = slicePosition[i2 + 1];
					slicePosition[i2 + 1] = positionSwap;
					String pathSwap = sortedPath[i2];
					sortedPath[i2] = sortedPath[i2 + 1];
					sortedPath[i2 + 1] = pathSwap;
				}
			}
		}
		// bubblesort end
		return sortedPath;
	}

	/**
	 * analisi di un profilo
	 * 
	 * @param imp1
	 *            Immagine da analizzare
	 * @param vetRefPosition
	 *            Coordinate riferimento posizionato dall'operatore [ xStart,
	 *            yStart, xEnd, yEnd ]
	 * @param vetProfile
	 *            Coordinate profilo (da rototraslare)[ xStart, yStart, xEnd,
	 *            yEnd ]
	 * @param ra1
	 *            Diametro della Roi per calcolo baseline correction
	 * @param slab
	 *            Flag true=slab, false=cuneo
	 * @param invert
	 *            Flag true=erf invertita false=erf diritta
	 * @param step
	 *            Flag true=messaggi on, false=messaggi off
	 * @param bLabelSx
	 *            Flag true= label a sx, false=label a dx
	 * @return outFwhm[0]=FWHM (mm)
	 * @return outFwhm[1]=peak position (mm)
	 */

	public double[] analProf(ImagePlus imp1, double[] vetRefPosition,
			double[] vetProfile, int ra1, boolean slab, boolean invert,
			boolean step, boolean bLabelSx, double dimPixel) {

		/*
		 * Aggiornamento del 29 gennaio 2007 analProf dà in uscita i valori in
		 * millimetri, anzichè in pixel
		 */

		if (imp1 == null)
			return null;

		int mra = ra1 / 2;
		double[] msd1; // vettore output rototrasl coordinate
		msd1 = UtilAyv.coord2D2(vetRefPosition, vetProfile[0] - mra,
				vetProfile[1] - mra, false);
		int c2x = (int) msd1[0]; // coord rototrasl centro roi sx
		int c2y = (int) msd1[1]; // coord rototrasl centro roi sx

		// nota tecnica: quando si definisce una ROI con setRoi (ovale o
		// rettangolare che sia) passiamo a ImageJ le coordinate dell'angolo in
		// alto a Sx del BoundingRectangle per cui dobbiamo sempre includere nei
		// calcoli il raggio Roi

		// prima roi per baseline correction
		imp1.setRoi(new OvalRoi(c2x, c2y, ra1, ra1));
		imp1.updateAndDraw();
		ImageStatistics statC = imp1.getStatistics();
		if (step) {
			imp1.updateAndDraw();
			ButtonMessages.ModelessMsg(
					"primo centro c2x=" + c2x + " c2y=" + c2y + " ra1=" + ra1
							+ "  media=" + statC.mean + "   <51>", "CONTINUA");
		}

		msd1 = UtilAyv.coord2D2(vetRefPosition, vetProfile[2] - mra,
				vetProfile[3] - mra, false);
		int d2x = (int) msd1[0];
		int d2y = (int) msd1[1];

		// seconda roi per baseline correction
		imp1.setRoi(new OvalRoi(d2x, d2y, ra1, ra1));
		ImageStatistics statD = imp1.getStatistics();
		if (step) {
			imp1.updateAndDraw();
			ButtonMessages.ModelessMsg(
					"secondo centro d2x=" + d2x + " d2y=" + d2y + " ra1=" + ra1
							+ "  media=" + statD.mean + "   <52>", "CONTINUA");
		}
		// inizio wideline
		msd1 = UtilAyv.coord2D2(vetRefPosition, vetProfile[0], vetProfile[1],
				false);
		c2x = (int) msd1[0];
		c2y = (int) msd1[1];
		// fine wideline
		msd1 = UtilAyv.coord2D2(vetRefPosition, vetProfile[2], vetProfile[3],
				false);
		d2x = (int) msd1[0];
		d2y = (int) msd1[1];

		// linea calcolo segnale mediato
		Line.setWidth(11);
		double[] profiM1 = getLinePixels(imp1, c2x, c2y, d2x, d2y);

		if (step) {
			imp1.updateAndDraw();
			msgWideline();
			createPlot2(profiM1, true, bLabelSx, "Profilo mediato", false);
			msgSlab();
		}

		double[] profiB1 = baselineCorrection(profiM1, statC.mean, statD.mean);

		if (step) {
			createPlot2(profiB1, true, bLabelSx,
					"Profilo mediato + baseline correction", true);
			msgBaseline();
		}
		int isd3[];
		double[] outFwhm;
		if (slab) {
			isd3 = analPlot1(profiB1, slab);
			outFwhm = calcFwhm(isd3, profiB1, slab, dimPixel);
			if (step) {
				createPlot2(profiB1, slab, bLabelSx,
						"plot mediato + baseline + FWHM", true);
				msgFwhm();
			}
			Line.setWidth(1);
			return (outFwhm);
		} else {
			double[] profiE1 = createErf(profiB1, invert); // profilo con ERF

			isd3 = analPlot1(profiE1, slab);

			outFwhm = calcFwhm(isd3, profiE1, slab, dimPixel);

			if (step) {
				createPlot2(profiE1, slab, bLabelSx,
						"plot ERF con smooth 3x3 e FWHM", true);
				msgErf();
			}
			Line.setWidth(1);
			return (outFwhm);
		}
	}

	/**
	 * calcolo FWHM del profilo assegnato
	 * 
	 * @param isd
	 *            coordinate sul profilo sopra e sotto metà altezza
	 * @param profile
	 *            profilo da analizzare
	 * @param bslab
	 *            true se slab, false se cuneo
	 * @return out[0] fwhm calcolata (mm)
	 * @return out[1] peak position (mm)
	 */
	public double[] calcFwhm(int[] isd, double[] profile, boolean bslab,
			double dimPixel) {

		double peak = 0;
		double[] a = Tools.getMinMax(profile);
		double min1 = a[0];
		double max1 = 0;
		if (bslab)
			max1 = a[1];
		else
			max1 = 0;
		// interpolazione lineare sinistra
		double px0 = isd[0];
		double px1 = isd[1];
		double py0 = profile[isd[0]];
		double py1 = profile[isd[1]];
		double py2 = (max1 - min1) / 2.0 + min1;
		double px2 = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);
		double sx = px2;
		// interpolazione lineare destra
		px0 = isd[2];
		px1 = isd[3];
		py0 = profile[isd[2]];
		py1 = profile[isd[3]];
		py2 = (max1 - min1) / 2.0 + min1;
		px2 = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);
		double dx = px2;
		double fwhm = (dx - sx) * dimPixel;

		for (int i1 = 0; i1 < profile.length; i1++) {
			if (profile[i1] == min1)
				peak = i1 * dimPixel;
		}
		double[] out = { fwhm, peak };
		return (out);
	} // calcFwhm

	/**
	 * assegna un profilo su cui effettuare i calcoli
	 * 
	 * @param imp1
	 *            immagine su cui effettuare il profilo
	 * @param x1
	 *            coordinata x start profilo
	 * @param y1
	 *            coordinata y start profilo
	 * @param x2
	 *            coordinata x end profilo
	 * @param y2
	 *            coordinata y end profilo
	 * @return profilo wideline
	 */
	public double[] getLinePixels(ImagePlus imp1, int x1, int y1, int x2, int y2) {
		imp1.setRoi(new Line(x1, y1, x2, y2));
		imp1.updateAndDraw();
		Roi roi1 = imp1.getRoi();
		double[] profiM1 = ((Line) roi1).getPixels();
		return profiM1;
	}

	/**
	 * calcolo baseline correction del profilo assegnato
	 * 
	 * @param profile1
	 *            profilo da correggere
	 * @param media1
	 *            media sul fondo a sx
	 * @param media2
	 *            media sul fondo a dx
	 * @return profilo corretto
	 */
	public double[] baselineCorrection(double[] profile1, double media1,
			double media2) {

		int len1 = profile1.length;
		double diff1;
		double profile2[];
		profile2 = new double[len1];
		diff1 = (media1 - media2) / len1;
		for (int i1 = 0; i1 < len1; i1++)
			profile2[i1] = profile1[i1] + diff1 * i1;
		return profile2;
	} // baselineCorrection

	/**
	 * calcolo ERF
	 * 
	 * @param profile1
	 *            profilo da elaborare
	 * @param invert
	 *            true se da invertire
	 * @return profilo con ERF
	 */
	public double[] createErfOld(double[] profile1, boolean invert) {

		int len1 = profile1.length;
		//
		// eseguo tre smooth 07-02-05, questo ci permette di ottenere risultati
		// affidabili non falsati da spikes
		//
		for (int j = 1; j < len1 - 1; j++)
			profile1[j] = (profile1[j - 1] + profile1[j] + profile1[j + 1]) / 3;
		for (int j = 1; j < len1 - 1; j++)
			profile1[j] = (profile1[j - 1] + profile1[j] + profile1[j + 1]) / 3;
		for (int j = 1; j < len1 - 1; j++)
			profile1[j] = (profile1[j - 1] + profile1[j] + profile1[j + 1]) / 3;
		for (int j = 1; j < len1 - 1; j++)
			profile1[j] = (profile1[j - 1] + profile1[j] + profile1[j + 1]) / 3;

		double[] sorted = new double[len1];
		for (int j = 0; j < sorted.length; j++)
			sorted[j] = profile1[j];
		Arrays.sort(sorted);
		// ora il valore minimo dell'array corrisponde a quello di indice 0
		// possiamo interrompere la generazione dell'erf quando il
		// segnale del cuneo raggiunge questo valore
		int stop = 0;

		for (int j = 0; j < len1 - 1; j++)
			if (profile1[j] == sorted[0]) {
				stop = j;
				break;
			}
		double[] erf = new double[len1];
		if (invert) {
			for (int j = 0; j < stop; j++)
				erf[j] = (profile1[j] - profile1[j + 1]) * (-1);
		} else {
			for (int j = profile1.length - 1; j > stop; j--)
				erf[j] = (profile1[j] - profile1[j - 1]) * (-1);
		}
		erf[len1 - 1] = erf[len1 - 2];
		return (erf);
	} // createErf

	/**
	 * calcolo ERF
	 * 
	 * @param profile1
	 *            profilo da elaborare
	 * @param invert
	 *            true se da invertire
	 * @return profilo con ERF
	 */
	public double[] createErf(double[] profile1, boolean invert) {

		int len1 = profile1.length;
		//
		// eseguo tre smooth 07-02-05, questo ci permette di ottenere risultati
		// affidabili non falsati da spikes
		//
		for (int j = 1; j < len1 - 1; j++)
			profile1[j] = (profile1[j - 1] + profile1[j] + profile1[j + 1]) / 3;
		for (int j = 1; j < len1 - 1; j++)
			profile1[j] = (profile1[j - 1] + profile1[j] + profile1[j + 1]) / 3;
		for (int j = 1; j < len1 - 1; j++)
			profile1[j] = (profile1[j - 1] + profile1[j] + profile1[j + 1]) / 3;
		for (int j = 1; j < len1 - 1; j++)
			profile1[j] = (profile1[j - 1] + profile1[j] + profile1[j + 1]) / 3;

		double[] erf = new double[len1];
		// if (invert) {
		for (int j = 0; j < profile1.length - 1; j++)
			erf[j] = (profile1[j] - profile1[j + 1]) * (-1);
		// } else {
		// for (int j = profile1.length - 1; j >= 0; j--)
		// erf[j] = (profile1[j] - profile1[j - 1]) * (-1);
		// }
		erf[len1 - 1] = erf[len1 - 2];

		// Anzichè utilizzare algoritmi di ricerca dei picchi, cerco il minimo
		// ed il massimo. Il valore assoluto più grande corrisponderà all'angolo
		// a 90° che non ci interessa. A questo punto posso portare a zero tutti
		// i valori del medesimo segno. Resterà così solo il
		// picco meno alto, corrispondente all'erf della rampa del cuneo.

		double[] minMax = Tools.getMinMax(erf);
		double min = minMax[0];
		double max = minMax[1];

		if (Math.abs(min) > Math.abs(max) && min < 0) {
			// se il minimo è di valore assoluto più grande, allora porto a 0
			// tutti i valori minori di 0
			for (int i1 = 0; i1 < erf.length; i1++) {
				if (erf[i1] < 0)
					erf[i1] = 0;
			}
			// e poi cambio il tutto di segno, poichè voglio il picco verso il
			// basso
			for (int i1 = 0; i1 < erf.length; i1++) {
				erf[i1] *= -1;
			}

		} else if (Math.abs(min) < Math.abs(max) && max > 0) {
			// se il massimo è di valore assoluto più grande, allora porto a 0
			// tutti i valori maggiori di 0
			for (int i1 = 0; i1 < erf.length; i1++) {
				if (erf[i1] > 0)
					erf[i1] = 0;
			}

		} else
			MyLog.waitHere("QUESTA E'UNA STRANA ERF");

		return (erf);
	} // createErf

	/**
	 * display di un profilo con linea a metà altezza
	 * 
	 * @param profile1
	 *            profilo in ingresso
	 * @param bslab
	 *            true=slab false=cuneo
	 * @param bLabelSx
	 *            true=label a sx nel plot, false=label a dx
	 * @param sTitolo
	 *            titolo del grafico
	 * @param bFw
	 *            true=scritte x FWHM
	 */
	public void createPlot2(double[] profile1, boolean bslab, boolean bLabelSx,
			String sTitolo, boolean bFw) {
		int isd2[];
		double ddd[];
		double eee[];
		double fff[];
		double ggg[];
		double labPos;

		isd2 = analPlot1(profile1, bslab);

		double[] a = Tools.getMinMax(profile1);
		double min1 = a[0];
		double max1 = 0;
		if (bslab)
			max1 = a[1];
		else
			max1 = 0;

		double half = (max1 - min1) / 2 + min1;

		ddd = new double[2];
		eee = new double[2];
		fff = new double[2];
		ggg = new double[2];
		int len1 = profile1.length;
		double[] xcoord1 = new double[len1];
		for (int j = 0; j < len1; j++)
			xcoord1[j] = j;
		Plot plot = new Plot(sTitolo, "pixel", "valore", xcoord1, profile1);
		if (bslab)
			plot.setLimits(0, len1, min1, max1);
		else
			plot.setLimits(0, len1, min1, 30);
		plot.setLineWidth(1);
		plot.setColor(Color.blue);
		ddd[0] = (double) isd2[0];
		ddd[1] = (double) isd2[2];
		eee[0] = profile1[isd2[0]];
		eee[1] = profile1[isd2[2]];
		plot.addPoints(ddd, eee, PlotWindow.X);
		ddd[0] = (double) isd2[1];
		ddd[1] = (double) isd2[3];
		eee[0] = profile1[isd2[1]];
		eee[1] = profile1[isd2[3]];
		plot.addPoints(ddd, eee, PlotWindow.CIRCLE);
		plot.changeFont(new Font("Helvetica", Font.PLAIN, 10));
		// interpolazione lineare sinistra
		double px0 = isd2[0];
		double px1 = isd2[1];
		double px2 = 0;
		double py0 = profile1[isd2[0]];
		double py1 = profile1[isd2[1]];
		double py2 = 0;
		py2 = half;
		px2 = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);
		double sx = px2;
		// interpolazione lineare destra
		px0 = isd2[2];
		px1 = isd2[3];
		py0 = profile1[isd2[2]];
		py1 = profile1[isd2[3]];
		py2 = half;
		px2 = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);
		double dx = px2;
		double fwhm = dx - sx;
		if (bLabelSx)
			labPos = 0.10;
		else
			labPos = 0.60;
		if (bFw) {
			plot.addLabel(labPos, 0.45, "peak / 2=   " + IJ.d2s(max1 / 2, 2));
			plot.addLabel(
					labPos,
					0.50,
					"down sx " + isd2[0] + "  =   "
							+ IJ.d2s(profile1[isd2[0]], 2));
			plot.addLabel(
					labPos,
					0.55,
					"down dx " + isd2[2] + "  =   "
							+ IJ.d2s(profile1[isd2[2]], 2));
			plot.addLabel(
					labPos,
					0.60,
					"up      sx " + isd2[1] + "  =   "
							+ IJ.d2s(profile1[isd2[1]], 2));
			plot.addLabel(
					labPos,
					0.65,
					"up      dx " + isd2[3] + "  =   "
							+ IJ.d2s(profile1[isd2[3]], 2));
			plot.addLabel(labPos, 0.70, "sx interp       =  " + IJ.d2s(sx, 2));
			plot.addLabel(labPos, 0.75, "dx interp       =  " + IJ.d2s(dx, 2));
			plot.addLabel(labPos, 0.80, "fwhm            =  " + IJ.d2s(fwhm, 2));
			plot.setColor(Color.green);
		}
		fff[0] = 0;
		fff[1] = len1;
		ggg[0] = half;
		ggg[1] = half;
		plot.addPoints(fff, ggg, PlotWindow.LINE);
		plot.setColor(Color.red);
		plot.show();

		plot.draw();

	}

	/**
	 * analisi di un profilo normale con ricerca punti sopra e sotto metà
	 * altezza
	 * 
	 * @param profile1
	 *            profilo da analizzare
	 * @param bSlab
	 *            true=slab false=cuneo
	 * @return isd[0] punto profilo sotto half a sx
	 * @return isd[1] punto profilo sopra half a sx
	 * @return isd[2] punto profilo sotto half a dx
	 * @return isd[3] punto profilo sopra half a dx
	 */
	public int[] analPlot1(double profile1[], boolean bSlab) {

		int sopra1 = 0;
		int sotto1 = 0;
		int sopra2 = 0;
		int sotto2 = 0;
		double max1 = 0;
		int i1;
		int[] isd;
		isd = new int[4];
		isd[0] = 0;
		isd[1] = 0;
		isd[2] = 0;
		isd[3] = 0;
		int len1 = profile1.length;

		double[] a = Tools.getMinMax(profile1);
		double min1 = a[0];
		if (bSlab)
			max1 = a[1];
		else
			max1 = 0;

		// calcolo metà altezza
		double half = (max1 - min1) / 2 + min1;
		// ricerca valore < half partendo da SX
		for (i1 = 0; i1 < len1 - 1; i1++) {
			if (profile1[i1] < half) {
				sotto1 = i1;
				break;
			}
		}
		// torno indietro e cerco il primo valore > half
		for (i1 = sotto1; i1 > 0; i1--) {
			if (profile1[i1] > half) {
				sopra1 = i1;
				break;
			}
		}
		// ricerca valore < half partendo da DX
		for (i1 = len1 - 1; i1 > 0; i1--) {
			if (profile1[i1] < half) {
				sotto2 = i1;
				break;
			}
		}
		// torno indietro e cerco il primo valore > half
		for (i1 = sotto2; i1 < len1 - 1; i1++) {
			if (profile1[i1] > half) {
				sopra2 = i1;
				break;
			}
		}
		isd[0] = sotto1;
		isd[1] = sopra1;
		isd[2] = sotto2;
		isd[3] = sopra2;
		return isd;
	} // analPlot1

	/**
	 * calcolo spessore di strato effettivo, apportando le correzioni per
	 * inclinazione e tilt (ciò che veniva effettuato dal foglio Excel)
	 * 
	 * @param R1
	 * @param R2
	 * @param sTeor
	 *            spessore teorico
	 * @return spessore dello strato
	 */
	public double[] spessStrato(double R1, double R2, double sTeor,
			double dimPix2) {

		double spessArray[]; // qui verranno messi i risultati
		spessArray = new double[4];
		double S1 = R1 * Math.tan(Math.toRadians(11));
		double S2 = R2 * Math.tan(Math.toRadians(11));
		double Sen22 = Math.sin(Math.toRadians(22));
		double aux1 = -(S1 - S2) / (S1 + S2);
		double aux4 = Math.asin(Sen22 * aux1);
		double tilt1Ramp = Math.toDegrees(0.5 * aux4);
		double aux2 = Math.tan(Math.toRadians(11.0 - tilt1Ramp));
		double aux3 = Math.tan(Math.toRadians(11.0 + tilt1Ramp));
		double S1Cor = aux3 * R1;
		double S2Cor = aux2 * R2;
		double accurSpess = 100.0 * (S1Cor - sTeor) / sTeor;
		double erroreR1 = dimPix2 * aux3;
		double erroreR2 = dimPix2 * aux2;
		double erroreTot = Math.sqrt(erroreR1 * erroreR1 + erroreR2 * erroreR2);
		double erroreSper = 100.0 * erroreTot / sTeor;
		spessArray[0] = S1Cor;
		spessArray[1] = S2Cor;
		spessArray[2] = erroreSper;
		spessArray[3] = accurSpess;
		return spessArray;
	}

	/**
	 * impedisce che nelle preferenze di ImageJ vengano memorizzati segmenti con
	 * valori assurdi, crea un area al di fuori della quale il valore
	 * memorizzato è quello di default, inoltre non permette che il segmento sia
	 * più corto di 10 pixel
	 * 
	 * @param vetLine
	 *            coordinate linea [ xStart, yStart, xEnd, yEnd ]
	 * @param matrix
	 *            matrice immagine
	 * @return true se interdetta memorizzazione
	 */
	public boolean interdiction(double[] vetLine, int matrix) {

		if (vetLine[0] < 10)
			return true;
		if (vetLine[0] > (matrix - 10))
			return true;
		if (vetLine[2] < 10)
			return true;
		if (vetLine[2] > (matrix - 10))
			return true;
		double len = Math.sqrt(Math.pow(vetLine[0] - vetLine[2], 2)
				+ Math.pow(vetLine[1] - vetLine[3], 2));
		if (len < 10)
			return true;
		// se viene ribaltato il segmento interviene
		if (vetLine[1] > vetLine[3])
			return true;

		return false;
	}

	/**
	 * Restituisce i valori di riferimento per le immagini Siemens (e simili), i
	 * valori non dovrebbero mai cambiare, salvo che per modifiche a ImageJ
	 * 
	 * @return
	 */
	public double[] referenceSiemens() {

		double slicePos = 10;
		double fwhmSlab1 = 10.090520971808136;
		double peak_slab1 = 42.96875;
		double fwhm_slab2 = 13.02010356505786;
		double peak_slab2 = 57.03125;
		double fwhm_cuneo3 = 8.090445406547264;
		double peak_cuneo3 = 44.53125;
		double fwhm_cuneo4 = 10.550518461420733;
		double peak_cuneo4 = 64.84375;
		double S1CorSlab = 2.211326690998348;
		double S2CorSlab = 2.211326690998348;
		double ErrSperSlab = 10.830367502157365;
		double AccurSpesSlab = 10.5663345499174;
		double S1CorCuneo = 1.7812946948464947;
		double S2CorCuneo = 1.7812946948464943;
		double ErrSperCuneo = 10.838079119386789;
		double AccurSpesCuneo = -10.935265257675264;
		double Accettab = 0.0;
		double DimPix = 0.78125;
		double Thick = 2.0;
		double Spacing = 0.0;
		double[] vetReference = { slicePos, fwhmSlab1, peak_slab1, fwhm_slab2,
				peak_slab2, fwhm_cuneo3, peak_cuneo3, fwhm_cuneo4, peak_cuneo4,
				S1CorSlab, S2CorSlab, ErrSperSlab, AccurSpesSlab, S1CorCuneo,
				S2CorCuneo, ErrSperCuneo, AccurSpesCuneo, Accettab, DimPix,
				Thick, Spacing };
		return vetReference;
	}

	/**
	 * Restituisce i valori di riferimento per le immagini Ge (e simili), i
	 * valori non dovrebbero mai cambiare, salvo che per modifiche a ImageJ
	 * 
	 * @return
	 */
	public double[] referenceGe() {

		double slicePos = 1.71565;
		double fwhmSlab1 = 23.067207264738247;
		double peak_slab1 = 19.531500339508057;
		double fwhm_slab2 = 75.15392036949994;
		double peak_slab2 = 80.4697813987732;
		double fwhm_cuneo3 = 23.017457348952945;
		double peak_cuneo3 = 29.687880516052246;
		double fwhm_cuneo4 = 22.55055404276223;
		double peak_cuneo4 = 78.12600135803223;
		double S1CorSlab = 6.933279823491047;
		double S2CorSlab = 6.933279823491049;
		double ErrSperSlab = 4.9126882060578065;
		double AccurSpesSlab = 38.66559646982093;
		double S1CorCuneo = 4.428314036685271;
		double S2CorCuneo = 4.428314036685271;
		double ErrSperCuneo = 4.2955355260231265;
		double AccurSpesCuneo = -11.433719266294577;
		double Accettab = 0.0;
		double DimPix = 0.7812600135803223;
		double Thick = 5.0;
		double Spacing = 5.0;

		double[] vetReference = { slicePos, fwhmSlab1, peak_slab1, fwhm_slab2,
				peak_slab2, fwhm_cuneo3, peak_cuneo3, fwhm_cuneo4, peak_cuneo4,
				S1CorSlab, S2CorSlab, ErrSperSlab, AccurSpesSlab, S1CorCuneo,
				S2CorCuneo, ErrSperCuneo, AccurSpesCuneo, Accettab, DimPix,
				Thick, Spacing };
		return vetReference;
	}

	public static void msgSquare() {
		ButtonMessages.ModelessMsg(
				"Far coincidere il segmento  con il lato sx del quadrato",
				"CONTINUA");
	}

	public static void msgSquareCoordinates(double[] vetReference) {
		ButtonMessages.ModelessMsg("coordinate posizionamento ax= "
				+ vetReference[0] + "   ay = " + vetReference[1] + "   bx = "
				+ vetReference[2] + "  by = " + vetReference[3], "CONTINUA");
	}

	public static int msgAccept() {
		int userSelection = ButtonMessages.ModelessMsg(
				"Accettabilità immagine   <08>", "ELABORA", "SALTA");
		return userSelection;
	}

	public static void msgProfile() {
		ButtonMessages.ModelessMsg("Analisi profilo e fwhm", "CONTINUA");
	}

	public static void msgWideline() {
		ButtonMessages.ModelessMsg("Profilo wideline", "CONTINUA");
	}

	public static void msgSlab() {
		ButtonMessages.ModelessMsg("Profilo mediato slab", "CONTINUA");
	}

	public static void msgBaseline() {
		ButtonMessages.ModelessMsg("Profilo mediato e baseline correction",
				"CONTINUA");
	}

	public static void msgFwhm() {
		ButtonMessages.ModelessMsg("Profilo mediato + baseline + FWHM",
				"CONTINUA");
	}

	public static void msgErf() {
		ButtonMessages.ModelessMsg("Profilo ERF + smooth 3x3 + FWHM",
				"CONTINUA");
	}

}
