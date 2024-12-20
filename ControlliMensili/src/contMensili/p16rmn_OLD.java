package contMensili;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.gui.Roi;
import ij.io.OpenDialog;
import ij.measure.CurveFitter;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.util.Tools;
import utils.AboutBox;
import utils.ArrayUtils;
import utils.ButtonMessages;
import utils.InputOutput;
import utils.MyConst;
import utils.MyLog;
import utils.MyMsg;
import utils.MyStackUtils;
import utils.MyVersionUtils;
import utils.ReadDicom;
import utils.ReportStandardInfo;
import utils.TableCode;
import utils.TableExpand;
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

public class p16rmn_OLD implements PlugIn, Measurements {

	static final int ABORT = 1;

	public static String VERSION = "SPESSORE FETTA";

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

		UtilAyv.setMyPrecision();

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

		String className = this.getClass().getName();
		String user1 = System.getProperty("user.name");
		TableCode tc1 = new TableCode();
		String iw2ayv1 = tc1.nameTable("codici", "csv");
		TableExpand tc2 = new TableExpand();
		String iw2ayv2 = tc1.nameTable("expand", "csv");

		VERSION = user1 + ":" + className + "build_" + MyVersion.getVersion() + ":iw2ayv_build_"
				+ MyVersionUtils.getVersion() + ":" + iw2ayv1 + ":" + iw2ayv2;

//		VERSION = className + "_build_" + MyVersion.getVersion() + "_iw2ayv_build_" + MyVersionUtils.getVersion();

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
				new AboutBox().about("Controllo Thickness", MyVersion.CURRENT_VERSION);
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
				boolean verbose = true;
				boolean test = false;
				ImagePlus imp0 = UtilAyv.openImageNoDisplay(path[0], true);
				double[] oldPosition = readReferences(imp0);
				new p16rmn_OLD().wrapThickness(path, "0", oldPosition, autoCalled, step, verbose, test);
				UtilAyv.afterWork();
				retry = true;
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

		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir + MyConst.SEQUENZE_FILE);

		boolean retry = false;
		boolean step = false;
		int userSelection1 = 0;
		do {
			if (nTokens == 1)
				// userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE1);
				userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE1,
						TableSequence.getCode(iw2ayvTable, vetRiga[0]), TableSequence.getCoil(iw2ayvTable, vetRiga[0]),
						vetRiga[0] + 1, TableSequence.getLength(iw2ayvTable));

			else
				// userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE2);
				userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE2,
						TableSequence.getCode(iw2ayvTable, vetRiga[0]), TableSequence.getCoil(iw2ayvTable, vetRiga[0]),
						vetRiga[0] + 1, TableSequence.getLength(iw2ayvTable));

			step = false;
			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				return 0;
			case 2:
				// new AboutBox().about("Controllo Thickness", this.getClass());
				new AboutBox().about("Controllo Thickness", MyVersion.CURRENT_VERSION);
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
				ResultsTable rt = new p16rmn_OLD().wrapThickness(path, autoArgs, vetRefPosition, autoCalled, step, verbose,
						test);
				UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, rt);

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
			int userSelection = ButtonMessages.ModelessMsg("Finito ?", "SELEZIONA ALTRA IMMAGINE", "FINE SELEZIONE");
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
				String[] path1 = new InputOutput().findListTestImages2(MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
				double[] vetRefPosition = MyConst.P6_REFERENCE_LINE_GE;
				// String[] path={"./Test2/BT2A_testP6"};
				String autoArgs = "0";
				boolean autoCalled = false;
				boolean step = false;
				boolean verbose = false;
				boolean test = true;
				ResultsTable rt = new p16rmn_OLD().mainThickness(path1, autoArgs, vetRefPosition, autoCalled, step,
						verbose, test);
				rt.show("Results");
				MyLog.waitHere("verifica results table");
				double[] vetResults = UtilAyv.vectorizeResults(rt);
				boolean ok = UtilAyv.verifyResults1(vetResults, referenceGe(), MyConst.P6_vetName);
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
				String[] path1 = new InputOutput().findListTestImages2(MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
				double[] vetRefPosition = MyConst.P6_REFERENCE_LINE_SIEMENS;
				// String[] path={"./Test2/BT2A_testP6"};
				String autoArgs = "0";
				boolean autoCalled = false;
				boolean step = false;
				boolean verbose = false;
				boolean test = true;

				ResultsTable rt = new p16rmn_OLD().mainThickness(path1, autoArgs, vetRefPosition, autoCalled, step,
						verbose, test);

				double[] vetResults = UtilAyv.vectorizeResults(rt);
				boolean ok = UtilAyv.verifyResults1(vetResults, referenceSiemens(), MyConst.P6_vetName);
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
		String[] path1 = new InputOutput().findListTestImages2(MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

		double[] vetRefPosition = MyConst.P6_REFERENCE_LINE_SIEMENS;
		String autoArgs = "-1";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = false;
		boolean test = true;

		ResultsTable rt = new p16rmn_OLD().mainThickness(path1, autoArgs, vetRefPosition, autoCalled, step, verbose, test);

		double[] vetResults = UtilAyv.vectorizeResults(rt);
		boolean ok = UtilAyv.verifyResults1(vetResults, referenceSiemens(), MyConst.P6_vetName);
		if (ok)
			IJ.log("Il test di p6rmn_ THICKNESS � stato SUPERATO");
		else
			IJ.log("Il test di p6rmn_ THICKNESS evidenzia degli ERRORI");
	}

	public String[] loadPath(String autoArgs) {
		String fileDir = Prefs.get("prefer.string1", "./test2/");
		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir + MyConst.SEQUENZE_FILE);
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
				.readFloat(ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 1));

		double[] vetReference = new double[4];
		vetReference[0] = ReadDicom.readDouble(Prefs.get("prefer.p6rmnAx", "" + 50.0 / dimPixel));
		vetReference[1] = ReadDicom.readDouble(Prefs.get("prefer.p6rmnAy", "" + 60.0 / dimPixel));
		vetReference[2] = ReadDicom.readDouble(Prefs.get("prefer.p6rmnBx", "" + 50.0 / dimPixel));
		vetReference[3] = ReadDicom.readDouble(Prefs.get("prefer.p6rmnBy", "" + 215.0 / dimPixel));
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

	public ResultsTable wrapThickness(String[] path, String autoArgs, double[] vetRefPosition, boolean autoCalled,
			boolean step, boolean verbose, boolean test) {
		boolean accetta = false;
		ResultsTable rt = null;

		do {
			rt = mainThickness(path, autoArgs, vetRefPosition, autoCalled, step, verbose, test);
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
		return rt;
	}

	// @SuppressWarnings("deprecation")
	public ResultsTable mainThickness(String[] path, String autoArgs, double[] vetRefPosition, boolean autoCalled,
			boolean step, boolean verbose, boolean test) {

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
		Overlay over3 = null;

		ImagePlus impStack = stackBuilder(path, true);

		impStack.setSliceWithoutUpdate(1);

		if (verbose)
			UtilAyv.showImageMaximized(impStack);
		float dimPixel = ReadDicom.readFloat(
				ReadDicom.readSubstring(ReadDicom.readDicomParameter(impStack, MyConst.DICOM_PIXEL_SPACING), 1));
		impStack.setRoi(new Line((int) vetRefPosition[0], (int) vetRefPosition[1], (int) vetRefPosition[2],
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

		double thick = ReadDicom.readDouble(ReadDicom.readDicomParameter(impStack, MyConst.DICOM_SLICE_THICKNESS));
		double spacing = ReadDicom
				.readDouble(ReadDicom.readDicomParameter(impStack, MyConst.DICOM_SPACING_BETWEEN_SLICES));

		nFrames = impStack.getStackSize();

		for (int w1 = 0; w1 < nFrames; w1++) {

			if (step)
				MyLog.waitHere("INIZIO w1= " + w1 + " / " + nFrames);
			ImagePlus imp3 = MyStackUtils.imageFromStack(impStack, w1 + 1);

			if (imp3 != null) {
				over3 = new Overlay();
				imp3.setOverlay(over3);
				Line lin1 = new Line((int) vetRefPosition[0], (int) vetRefPosition[1], (int) vetRefPosition[2],
						(int) vetRefPosition[3]);
				imp3.setRoi(lin1);
				over3.addElement(imp3.getRoi());
				imp3.updateAndDraw();
			}

			String pos2 = ReadDicom.readDicomParameter(imp3, MyConst.DICOM_IMAGE_POSITION);
			slicePos2[w1] = ReadDicom.readSubstring(pos2, 3);

			if (verbose)
				UtilAyv.showImageMaximized(imp3);
			if (impStack.getStackSize() > 1) {
				int userSelection3 = msgAccept();
				vetAccettab[w1] = userSelection3;
			}

			if (!step)
				imp3.hide();

			// ====== PRIMA SLAB ========
			if (step)
				MyLog.waitHere("PRIMA SLAB");

			double kappa = 156 / lato;
			int ra1 = (int) (13 / kappa);
			vetProfile[0] = 10 / kappa;
			vetProfile[1] = 26 / kappa;
			vetProfile[2] = 141 / kappa;
			vetProfile[3] = 26 / kappa;

			boolean isSlab = true;
			boolean invertErf = false;
			boolean putLabelSx = false;
			if (imp3.isVisible()) {
				imp3.getWindow().toFront();
			}

			double[] dsd1 = analProf(imp3, vetRefPosition, vetProfile, ra1, isSlab, invertErf, step, putLabelSx,
					dimPixel, over3);

			fwhmSlice1[w1] = dsd1[0];
			peakPositionSlice1[w1] = dsd1[1];

			if (imp3.isVisible())
				imp3.getWindow().toFront();

			if (step)
				closePlots();

			// ====== SECONDA SLAB ========
			if (step)
				MyLog.waitHere("SECONDA SLAB");

			ra1 = (int) (13 / kappa);
			vetProfile[0] = 10 / kappa;
			vetProfile[1] = 61 / kappa;
			vetProfile[2] = 141 / kappa;
			vetProfile[3] = 61 / kappa;

			isSlab = true;
			invertErf = false;
			putLabelSx = true;
			if (step) {
				imp3.getWindow().toFront();
			}
			double[] dsd2 = analProf(imp3, vetRefPosition, vetProfile, ra1, isSlab, invertErf, step, putLabelSx,
					dimPixel, over3);
			fwhmSlice2[w1] = dsd2[0];
			peakPositionSlice2[w1] = dsd2[1];

			if (imp3.isVisible())
				imp3.getWindow().toFront();
			double[] spessCor1 = spessStrato(dsd1[0], dsd2[0], (double) thick, dimPixel);

			vetS1CorSlab[w1] = spessCor1[0];
			vetS2CorSlab[w1] = spessCor1[1];
			vetErrSpessSlab[w1] = spessCor1[2];
			vetAccurSpessSlab[w1] = spessCor1[3];

			if (step)
				closePlots();

			// ====== PRIMO CUNEO ========
			if (step)
				MyLog.waitHere("PRIMO CUNEO");

			ra1 = (int) (13 / kappa);
			vetProfile[0] = 10 / kappa;
			vetProfile[1] = 96 / kappa;
			vetProfile[2] = 141 / kappa;
			vetProfile[3] = 96 / kappa;

			isSlab = false;
			invertErf = true;
			putLabelSx = false;
			if (imp3.isVisible()) {
				imp3.getWindow().toFront();
			}

			double[] dsd3 = analProf(imp3, vetRefPosition, vetProfile, ra1, isSlab, invertErf, step, putLabelSx,
					dimPixel, over3);

			fwhmCuneo3[w1] = dsd3[0];
			if (UtilAyv.isNaN(dsd3[0]))
				MyLog.waitHere("NaN");
			peakPositionCuneo3[w1] = dsd3[1];

			if (imp3.isVisible())
				imp3.getWindow().toFront();

			if (step)
				closePlots();

			// ====== SECONDO CUNEO ========
			if (step)
				MyLog.waitHere("SECONDO CUNEO");

			ra1 = (int) (13 / kappa);
			vetProfile[0] = 10 / kappa;
			vetProfile[1] = 130 / kappa;
			vetProfile[2] = 141 / kappa;
			vetProfile[3] = 130 / kappa;

			isSlab = false;
			invertErf = false;
			putLabelSx = true;
			if (step) {
				imp3.getWindow().toFront();
			}
			double[] dsd4 = analProf(imp3, vetRefPosition, vetProfile, ra1, isSlab, invertErf, step, putLabelSx,
					dimPixel, over3);
			fwhmCuneo4[w1] = dsd4[0];
			peakPositionCuneo4[w1] = dsd4[1];

			if (imp3.isVisible())
				imp3.getWindow().toFront();
			double[] spessCor2 = spessStrato(dsd3[0], dsd4[0], (double) thick, dimPixel);
			vetS1CorCuneo[w1] = spessCor2[0];
			vetS2CorCuneo[w1] = spessCor2[1];
			vetErrSpessCuneo[w1] = spessCor2[2];
			vetAccurSpessCuneo[w1] = spessCor2[3];

			if (step)
				closePlots();

		}

		//
		// Salvataggio dei risultati nella results table
		//

		// String[][] tabCodici = new InputOutput().readFile1(MyConst.CODE_FILE,
		// MyConst.TOKENS4);

		// String[][] tabCodici = TableCode.loadMultipleTable(MyConst.CODE_GROUP);
		TableCode tc1 = new TableCode();
		String[][] tabCodici = tc1.loadMultipleTable("codici", ".csv");

		String[] info1 = ReportStandardInfo.getSimpleStandardInfo(path[0], impStack, tabCodici, VERSION, autoCalled);
		rt = ReportStandardInfo.putSimpleStandardInfoRT_new(info1);
		rt.showRowNumbers(true);


		String t1 = "TESTO";
		String s2 = "SLICE";
		String s3 = "seg_ax";
		String s4 = "seg_ay";
		String s5 = "seg_bx";
		String s6 = "seg_by";

		// rt.setHeading(++col, t1);
		// for (int j1 = 0; j1 < nFrames; j1++)
		// rt.setHeading(++col, "SLICE" + j1);
		// rt.setHeading(++col, "seg_ax");
		// rt.setHeading(++col, "seg_ay");
		// rt.setHeading(++col, "seg_bx");
		// rt.setHeading(++col, "seg_by");

		rt.addValue(t1, "slicePos");
		for (int j1 = 0; j1 < nFrames; j1++)
			rt.addValue(s2 + j1, UtilAyv.convertToDouble(slicePos2[j1]));
		rt.addValue(s3, vetRefPosition[0]);
		rt.addValue(s4, vetRefPosition[1]);
		rt.addValue(s5, vetRefPosition[2]);
		rt.addValue(s6, vetRefPosition[3]);

		rt.incrementCounter();
		rt.addValue(t1, "fwhm_slab1");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, fwhmSlice1[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "peak_slab1");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, peakPositionSlice1[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "fwhm_slab2");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, fwhmSlice2[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "peak_slab2");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, peakPositionSlice2[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "fwhm_cuneo3");
		for (int j1 = 0; j1 < nFrames; j1++) {
			if (UtilAyv.isNaN(fwhmCuneo3[j1]))
				MyLog.waitHere("BOH");
			rt.addValue(s2 + j1, fwhmCuneo3[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "peak_cuneo3");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, peakPositionCuneo3[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "fwhm_cuneo4");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, fwhmCuneo4[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "peak_cuneo4");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, peakPositionCuneo4[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "S1CorSlab");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, vetS1CorSlab[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "S2CorSlab");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, vetS2CorSlab[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "ErrSperSlab");
		for (int j1 = 0; j1 < nFrames; j1++)
			rt.addValue(s2 + j1, vetErrSpessSlab[j1]);

		rt.incrementCounter();
		rt.addValue(t1, "AccurSpesSlab");
		for (int j1 = 0; j1 < nFrames; j1++)
			rt.addValue(s2 + j1, vetAccurSpessSlab[j1]);

		rt.incrementCounter();
		rt.addValue(t1, "S1CorCuneo");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, vetS1CorCuneo[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "S2CorCuneo");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, vetS2CorCuneo[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "ErrSperCuneo");
		for (int j1 = 0; j1 < nFrames; j1++)
			rt.addValue(s2 + j1, vetErrSpessCuneo[j1]);

		rt.incrementCounter();
		rt.addValue(t1, "AccurSpesCuneo");
		for (int j1 = 0; j1 < nFrames; j1++)
			rt.addValue(s2 + j1, vetAccurSpessCuneo[j1]);

		rt.incrementCounter();
		rt.addValue(t1, "Accettab");
		for (int j1 = 0; j1 < nFrames; j1++)
			rt.addValue(s2 + j1, vetAccettab[j1]);

		rt.incrementCounter();
		rt.addValue(t1, "DimPix");
		rt.addValue(s2 + 0, dimPixel);

		rt.incrementCounter();
		rt.addValue(t1, "Thick");
		rt.addValue(s2 + 0, thick);

		rt.incrementCounter();
		rt.addValue(t1, "Spacing");
		rt.addValue(s2 + 0, spacing);

		return rt;
	}

	/**
	 * Costruisce uno stack a partire dal path delle immagini e lo riordina secondo
	 * la posizione delle fette.
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
			String dicomPosition = ReadDicom.readDicomParameter(imp0, MyConst.DICOM_IMAGE_POSITION);
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

	public double[] linePixels2D2(ImagePlus imp1, double[] vetReference, double[] vetLine, int width, Overlay over1) {

		double[] msd1; // vettore output rototrasl coordinate
		double c2x = 0;
		double c2y = 0;
		double d2x = 0;
		double d2y = 0;
		// rototraslazione coordinate inizio linea
		msd1 = UtilAyv.coord2D2(vetReference, vetLine[0], vetLine[1], false);
		c2x = (int) msd1[0];
		c2y = (int) msd1[1];
		// rototraslazione coordinate fine linea
		msd1 = UtilAyv.coord2D2(vetReference, vetLine[2], vetLine[3], false);
		d2x = (int) msd1[0];
		d2y = (int) msd1[1];
		Line.setWidth(width);
		imp1.setRoi(new Line(c2x, c2y, d2x, d2y));
		Line lin2 = (Line) imp1.getRoi();
		double[] linePixels = lin2.getPixels();
		over1.addElement(imp1.getRoi());
		imp1.updateAndDraw();
		Line.setWidth(1);
		return linePixels;
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
	 *            Coordinate profilo (da rototraslare)[ xStart, yStart, xEnd, yEnd ]
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

	public double[] analProf(ImagePlus imp1, double[] vetRefPosition, double[] vetProfile, int ra1, boolean slab,
			boolean invert, boolean step, boolean bLabelSx, double dimPixel, Overlay over1) {

		/*
		 * Aggiornamento del 29 gennaio 2007 analProf da' in uscita i valori in
		 * millimetri, anziche' in pixel
		 */

		if (imp1 == null)
			return null;
		double[] outFwhm = null;

		double[] msd1; // vettore output rototrasl coordinate
		double c2x;
		double c2y;
		double d2x;
		double d2y;

		// inizio wideline
		msd1 = UtilAyv.coord2D2(vetRefPosition, vetProfile[0], vetProfile[1], false);
		c2x = msd1[0];
		c2y = msd1[1];
		// fine wideline
		msd1 = UtilAyv.coord2D2(vetRefPosition, vetProfile[2], vetProfile[3], false);
		d2x = msd1[0];
		d2y = msd1[1];

		// linea calcolo segnale mediato
		Line.setWidth(11);
		imp1.setRoi(new Line(c2x, c2y, d2x, d2y));
		Line lin2 = (Line) imp1.getRoi();
		double[] profiM1 = lin2.getPixels();
		double[] vet2X = new double[profiM1.length];
		for (int i1 = 0; i1 < profiM1.length; i1++) {
			vet2X[i1] = i1;
		}

		MyLog.logVector(profiM1, "profiM1");

		double[] smoothM4 = smooth(profiM1);
		boolean updateimg = true;

		if (step) {
			Plot plot2 = new Plot("SEGNALE ORIGINALE (1 SMOOTH)", "pixel", "valore");
			plot2.setColor(Color.red);
			plot2.addPoints(vet2X, profiM1, Plot.LINE);
			plot2.setColor(Color.blue);
			plot2.addPoints(vet2X, smoothM4, Plot.LINE);
			plot2.addLegend("originale\n1 smooth");
			plot2.setLimitsToFit(updateimg);
			plot2.show();
			MyLog.waitHere("SEGNALE ORIGINALE");
		}

		if (!slab) {

			double[] profiE1 = createErf(profiM1, invert, step); // profilo con
																	// ERF
			smoothM4 = profiE1;
		}

		double minM4 = ArrayUtils.vetMin(smoothM4);

		double[] derivataM7 = derivata(smoothM4);

		if (step) {
			Plot plot3 = new Plot("DERIVATA COMUNE", "pixel", "valore");
			// plot3.setColor(Color.orange);
			// plot3.addPoints(vet2X, smoothM4, Plot.LINE);
			plot3.setColor(Color.blue);
			plot3.addPoints(vet2X, derivataM7, Plot.LINE);
			plot3.addLegend("derivata comune");
			plot3.setLimitsToFit(updateimg);
			plot3.show();
			MyLog.waitHere("DERIVATA COMUNE");
		}

		// int ordine = 6;
		int ordine = 8;
		double[] blueslope = angolo(derivataM7, ordine);
		if (step) {
			Plot plot5b = new Plot("ANGOLO ", "pixel", "valore");
			plot5b.setColor(Color.blue);
			plot5b.addPoints(vet2X, blueslope, Plot.LINE);
			plot5b.addLegend("angolo");
			plot5b.setLimitsToFit(updateimg);
			plot5b.show();
			MyLog.waitHere("angolo solo");
		}

		double[] orangeslope = reverse(angolo(reverse(derivataM7), ordine));

		int posmax = ArrayUtils.posMax(derivataM7);
		int posmin = ArrayUtils.posMin(derivataM7);
		boolean reverse = false;
		double soglia1 = 0.5;
		int uno = soglia(blueslope, soglia1, posmin, reverse);
		if (step)
			MyLog.waitHere("blueslope posmin= " + posmin + " reverse= " + reverse);

		reverse = true;
		int due = soglia(orangeslope, soglia1, posmax, reverse);
		if (step)
			MyLog.waitHere("orangeslope posmax= " + posmax + " reverse= " + reverse);

		double[] xpoints1 = new double[1];
		double[] ypoints1 = new double[1];
		xpoints1[0] = uno;
		ypoints1[0] = blueslope[uno];

		double[] xpoints2 = new double[1];
		double[] ypoints2 = new double[1];
		xpoints2[0] = due;
		ypoints2[0] = orangeslope[due];
		if (step) {
			Plot plot5 = new Plot("ELABORAZIONI DERIVATA + ANGOLO ", "pixel", "valore");
			plot5.setColor(Color.orange);
			plot5.addPoints(vet2X, derivataM7, Plot.LINE);
			plot5.setColor(Color.green);
			plot5.addPoints(vet2X, blueslope, Plot.LINE);
			plot5.setColor(Color.black);
			plot5.addPoints(xpoints1, ypoints1, Plot.CIRCLE);
			plot5.addLegend("derivata\nangolo\nendpoint");
			plot5.setLimitsToFit(updateimg);
			plot5.show();
			MyLog.waitHere("angolo lato DX");
		}
		if (step) {
			Plot plot5a = new Plot("ELABORAZIONI DERIVATA + ANGOLO ", "pixel", "valore");
			plot5a.setColor(Color.orange);
			plot5a.addPoints(vet2X, derivataM7, Plot.LINE);
			plot5a.setColor(Color.red);
			plot5a.addPoints(vet2X, orangeslope, Plot.LINE);
			plot5a.setColor(Color.black);
			plot5a.addPoints(xpoints2, ypoints2, Plot.CIRCLE);
			plot5a.addLegend("derivata\nangolo(rev)\nstartpoint");
			plot5a.setLimitsToFit(updateimg);
			plot5a.show();
			MyLog.waitHere("angolo lato SX");
		}

		double[] ypoints3 = new double[1];
		ypoints3[0] = derivataM7[uno];
		double[] ypoints4 = new double[1];
		ypoints4[0] = derivataM7[due];

		if (step) {
			Plot plot5b = new Plot("DERIVATA CON PUNTI", "pixel", "valore");
			plot5b.setColor(Color.green);
			plot5b.addPoints(vet2X, derivataM7, Plot.LINE);
			plot5b.setColor(Color.black);
			plot5b.addPoints(xpoints1, ypoints3, Plot.CIRCLE);
			plot5b.addPoints(xpoints2, ypoints4, Plot.CIRCLE);
			plot5b.addLegend("derivata\nxpoints1\nxpoints2");
			plot5b.setLimitsToFit(updateimg);
			plot5b.show();
		}
		if (step)
			MyLog.waitHere("DERIVATA CON PUNTI xpoints1= " + xpoints1[0] + " ypoints3= " + ypoints3[0] + " xpoints2= "
					+ xpoints2[0] + " ypoints4= " + ypoints4[0]);

		// ottenuti i punti uno e due effettuo la potatura dei dati ivi compresi

		ArrayList<ArrayList<Double>> arrprofile3 = potatura(smoothM4, uno, due);
		double[] xprofile3 = ArrayUtils.arrayListToArrayDouble(arrprofile3.get(0));
		double[] yprofile3 = ArrayUtils.arrayListToArrayDouble(arrprofile3.get(1));

		// double[] derivata2_M8 = derivata(derivataM7);

		CurveFitter cf1 = new CurveFitter(xprofile3, yprofile3);
		cf1.doFit(CurveFitter.POLY2);
		double[] param1 = cf1.getParams();
		double[] vetfit = fitResult3(vet2X, param1);
		double[] correctedM4 = new double[vet2X.length];
		// double minC4 = ArrayUtils.vetMin(correctedM4);
		for (int i1 = 0; i1 < vet2X.length; i1++) {
			correctedM4[i1] = (smoothM4[i1] - vetfit[i1]);
		}
		double minF4 = ArrayUtils.vetMin(correctedM4);
		double kappa = minM4 - minF4;
		for (int i1 = 0; i1 < vet2X.length; i1++) {
			correctedM4[i1] = correctedM4[i1] + kappa;
		}

		if (step) {
			Plot plot6 = new Plot("FIT POLY2 FONDO", "pixel", "valore");
			plot6.setColor(Color.orange);
			plot6.addPoints(vet2X, smoothM4, Plot.LINE);
			plot6.setColor(Color.blue);
			plot6.addPoints(xprofile3, yprofile3, Plot.CROSS);
			plot6.setColor(Color.green);
			plot6.addPoints(vet2X, vetfit, Plot.LINE);
			plot6.addLegend("segnale\nfondo\nPOLY2suFondo");
			plot6.setLimitsToFit(updateimg);
			plot6.show();
			MyLog.waitHere("FONDO RILEVATO");

			Plot plot7 = new Plot("FONDO CORRETTO", "pixel", "valore");
			plot7.setColor(Color.orange);
			plot7.addPoints(vet2X, smoothM4, Plot.LINE);
			plot7.setColor(Color.blue);
			plot7.addPoints(vet2X, correctedM4, Plot.LINE);
			plot7.addLegend("originale\ncorretto");
			plot7.setLimitsToFit(updateimg);
			plot7.show();
			MyLog.waitHere("FONDO CORRETTO");
		}

		if (step) {
			int[] isd1 = analPlot1(smoothM4, slab);
			outFwhm = calcFwhm(isd1, smoothM4, slab, dimPixel);
			createPlot2(smoothM4, slab, dimPixel, bLabelSx, "plot segnale + FWHM", true);
			if (step)
				MyLog.waitHere("FWHM SENZA CORREZIONE");
		}

		int[] isd2 = analPlot1(correctedM4, slab);
		outFwhm = calcFwhm(isd2, correctedM4, slab, dimPixel);
		if (step)
			createPlot2(correctedM4, slab, dimPixel, bLabelSx, "plot corretto + FWHM", true);
		if (step)
			MyLog.waitHere("FWHM CON CORREZIONE");
		Line.setWidth(1);
		if (step)
			MyLog.waitHere("FINE ANALPROF");

		return (outFwhm);
	}

	public void closePlots() {
		// chiudo i plot esistenti plot=immagine senza info
		String[] vetNames = WindowManager.getImageTitles();
		for (int i1 = 0; i1 < vetNames.length; i1++) {
			ImagePlus impx = WindowManager.getImage(vetNames[i1]);
			if (impx.getInfoProperty() == null && impx.getBitDepth() == 8)
				impx.close();
		}
	}

	/**
	 * calcolo FWHM del profilo assegnato
	 * 
	 * @param isd
	 *            coordinate sul profilo sopra e sotto met� altezza
	 * @param profile
	 *            profilo da analizzare
	 * @param bslab
	 *            true se slab, false se cuneo
	 * @return out[0] fwhm calcolata (mm)
	 * @return out[1] peak position (mm)
	 */
	public double[] calcFwhm(int[] isd, double[] profile, boolean bslab, double dimPixel) {

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
		if (fwhm == Double.NaN) {
			MyLog.waitHere("isd[2]= " + isd[2] + " isd[3]= " + isd[3]);
			errorlog(profile, 0, 0, bslab);
		}

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
	// public double[] getLinePixels(ImagePlus imp1, int x1, int y1, int x2, int
	// y2) {
	// imp1.setRoi(new Line(x1, y1, x2, y2));
	// imp1.updateAndDraw();
	// Roi roi1 = imp1.getRoi();
	// double[] profiM1 = ((Line) roi1).getPixels();
	// return profiM1;
	// }
	//
	// public double[] getLinePixels(ImagePlus imp1, double x1, double y1,
	// double x2, double y2) {
	// imp1.setRoi(new Line(x1, y1, x2, y2));
	// imp1.updateAndDraw();
	// Roi roi1 = imp1.getRoi();
	// double[] profiM1 = ((Line) roi1).getPixels();
	// return profiM1;
	// }

	/**
	 * Log dei risulytati del fit polinomiale
	 * 
	 * @param cf1
	 *            curve fitter dopo il fit
	 */
	public static void logCurveFitter(CurveFitter cf1) {
		double[] para = cf1.getParams();

		IJ.log("-----------");
		IJ.log("ImageJ default report (few digits for parameters!)");
		IJ.log(cf1.getResultString());
		IJ.log("-----------");
		IJ.log("formula= " + cf1.getFormula());
		IJ.log("iterations= " + cf1.getIterations());
		IJ.log("maxIterations= " + cf1.getMaxIterations());
		IJ.log("restarts= " + cf1.getRestarts());
		IJ.log("sumResiduals= " + cf1.getSumResidualsSqr());
		IJ.log("standdev= " + cf1.getSD());
		IJ.log("R^2= " + cf1.getRSquared());
		IJ.log("a= " + para[0]);
		IJ.log("b= " + para[1]);
		IJ.log("c= " + para[2]);
		IJ.log("-----------");
	}

	/**
	 * Dati i parametri di una curva ed un vettore x, calcola i corrispondenti
	 * valori di y
	 * 
	 * @param vetX
	 *            vettore delle ascisse
	 * @param para
	 *            parametri della curva
	 * @return vettore delle ordinate
	 */
	public static double[] fitResult3(double[] vetX, double[] para) {
		double[] out = new double[vetX.length];

		for (int i1 = 0; i1 < vetX.length; i1++) {
			double x = vetX[i1];
			double y = para[0] + para[1] * x + para[2] * x * x;
			out[i1] = y;
		}
		return out;
	}

	public static double[] fitResult2(double[] vetX, double[] para) {
		double[] out = new double[vetX.length];

		for (int i1 = 0; i1 < vetX.length; i1++) {
			double x = vetX[i1];
			double y = para[0] + para[1] * x;
			out[i1] = y;
		}
		return out;
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
	public double[] baselineCorrection(double[] profile1, double media1, double media2) {

		int len1 = profile1.length;
		double diff1;
		double profile2[];
		profile2 = new double[len1];
		diff1 = (media1 - media2) / len1;
		for (int i1 = 0; i1 < len1; i1++)
			profile2[i1] = profile1[i1] + diff1 * i1;
		return profile2;
	}

	public double[] baselineCorrection2(double[] profile1, double[] baseline) {

		int len1 = profile1.length;
		double[] profile2 = new double[len1];
		for (int i1 = 0; i1 < len1; i1++)
			profile2[i1] = profile1[i1] - baseline[i1];
		return profile2;
	}

	public double[] derivata(double[] profile1) {

		double[] profile2 = new double[profile1.length];
		for (int i1 = 0; i1 < profile1.length - 1; i1++)
			profile2[i1] = (profile1[i1] - profile1[i1 + 1]);
		// metto l'ultimo pixel uguale al penultimo
		profile2[profile2.length - 1] = profile2[profile2.length - 4];
		profile2[profile2.length - 2] = profile2[profile2.length - 4];
		profile2[profile2.length - 3] = profile2[profile2.length - 4];
		return profile2;
	}

	public double[] reverse(double[] profile1) {
		double[] vetreverse = new double[profile1.length];
		for (int i1 = 0; i1 < profile1.length; i1++) {
			vetreverse[profile1.length - 1 - i1] = profile1[i1];
		}
		return vetreverse;
	}

	public double[] angolo(double[] profile1, int ordine) {

		double x0 = 0;
		double y0 = 0;
		double x1 = 0;
		double y1 = 0;
		double[] vetslope = new double[profile1.length];
		for (int i1 = ordine; i1 < profile1.length - ordine - 1; i1++) {
			x0 = i1;
			y0 = profile1[i1];
			x1 = i1 + ordine;
			y1 = profile1[i1 + ordine];
			vetslope[i1] = (y1 - y0) / (x1 - x0);
		}
		return vetslope;
	}

	public double[] angolo22222(double[] profile1, int ordine) {

		double x0 = 0;
		double y0 = 0;
		double x1 = 0;
		double y1 = 0;
		double[] vetslope = new double[profile1.length];
		for (int i1 = 1; i1 < profile1.length - 1; i1++) {
			x0 = i1 - 1;
			y0 = profile1[i1 - 1];
			x1 = i1 + 1;
			y1 = profile1[i1 + 1];
			vetslope[i1] = (y1 - y0) / (x1 - x0);
		}
		return vetslope;
	}

	public double[] angolo3333(double[] profile1, int ordine) {

		double x0 = 0;
		double y0 = 0;
		double x1 = 0;
		double y1 = 0;
		double[] vetslope = new double[profile1.length];
		for (int i1 = 0; i1 < profile1.length - ordine; i1++) {
			x0 = i1;
			y0 = profile1[i1];
			x1 = i1 + ordine;
			y1 = profile1[i1 + ordine];
			vetslope[i1] = (y1 - y0) / (x1 - x0);
		}
		return vetslope;
	}

	public int soglia(double[] profile1, double soglia1, int start, boolean reverse) {

		if (reverse) {
			for (int i1 = start; i1 >= 0; i1--)
				if (Math.abs(profile1[i1]) < soglia1)
					return i1;
		} else {
			// soglia1 = soglia1 * (-1.0);
			for (int i1 = start; i1 < profile1.length; i1++)
				if (Math.abs(profile1[i1]) < soglia1)
					return i1;
		}
		double[] vet2X = new double[profile1.length];
		for (int i1 = 0; i1 < profile1.length; i1++) {
			vet2X[i1] = i1;
		}
		double[] xx = new double[1];
		xx[0] = start;
		double[] yy = new double[1];
		yy[0] = profile1[start];
		double[] sogliax = new double[2];
		sogliax[0] = 0;
		sogliax[0] = profile1.length - 1;
		double[] sogliay = new double[2];
		sogliay[0] = soglia1;
		sogliay[0] = soglia1;

		errorlog(profile1, soglia1, start, reverse);
		return -1;
	}

	public ArrayList<ArrayList<Double>> potatura(double[] profile1, int uno, int due) {

		ArrayList<ArrayList<Double>> arrprofile2 = new ArrayList<ArrayList<Double>>();
		ArrayList<Double> arrx = new ArrayList<Double>();
		ArrayList<Double> arry = new ArrayList<Double>();
		// mi cautelo contro i punti passati al rovescio
		int lower = 0;
		int upper = 0;
		if (due > uno) {
			lower = uno;
			upper = due;
		} else {
			lower = due;
			upper = uno;
		}
		for (int i1 = 0; i1 <= lower; i1++) {
			arrx.add((double) i1);
			arry.add(profile1[i1]);
		}
		for (int i1 = upper; i1 < profile1.length; i1++) {
			arrx.add((double) i1);
			arry.add(profile1[i1]);
		}
		arrprofile2.add(arrx);
		arrprofile2.add(arry);
		return arrprofile2;
	}

	public double[] interpolazione(double[] profile1, int uno, int due) {
		// mi cautelo contro i punti passati al rovescio

		double[] profile2 = new double[profile1.length];
		for (int i1 = 0; i1 < profile1.length; i1++) {
			profile2[i1] = profile1[i1];
		}

		int lower = 0;
		int upper = 0;
		if (due > uno) {
			lower = uno;
			upper = due;
		} else {
			lower = due;
			upper = uno;
		}
		double x1 = lower;
		double y1 = profile2[lower];
		double x2 = upper;
		double y2 = profile2[upper];
		double m = (y2 - y1) / (x2 - x1);
		double q = (x2 * y1 - x1 * y2) / (x2 - x1);

		for (int i1 = (int) (x1 + 1); i1 < x2; i1++) {
			profile2[i1] = m * i1 + q;
		}

		return profile2;
	}

	public double[] smooth(double[] profile1) {

		double[] profile2 = new double[profile1.length];
		for (int i1 = 0; i1 < profile1.length - 1; i1++)
			profile2[i1] = (profile1[i1] + profile1[i1 + 1]) / 2;
		// metto l'ultimo pixel uguale al penultimo
		profile2[profile2.length - 1] = profile2[profile2.length - 2];
		return profile2;
	}

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
	public double[] createErf(double[] profile1, boolean invert, boolean step) {

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
		for (int j = 0; j < profile1.length - 1; j++) {
			erf[j] = (profile1[j] - profile1[j + 1]);
		}

		// elimino alcuni errori a inizio e fine profilo (non li voglio a zero)
		erf[len1 - 1] = erf[len1 - 2];
		erf[0] = erf[3];
		erf[1] = erf[3];
		erf[2] = erf[3];
		double[] vet2X = new double[profile1.length];
		for (int i1 = 0; i1 < profile1.length; i1++) {
			vet2X[i1] = i1;
		}
		boolean updateImg = true;
		if (step) {
			Plot plot1 = new Plot("COMPLETE ERF", "pixel", "valore");
			plot1.setColor(Color.red);
			// plot1.addPoints(vet2X, profile1, Plot.LINE);
			// plot1.setColor(Color.blue);
			plot1.addPoints(vet2X, erf, Plot.LINE);
			plot1.setLimitsToFit(updateImg);
			plot1.show();
			MyLog.waitHere("ERF TOTALE");
		}

		int ordine = 4;
		double[] blueslope = angolo(erf, ordine);
		double[] orangeslope = reverse(angolo(reverse(erf), ordine));
		int posmax1 = ArrayUtils.posMax(blueslope);
		int posmin1 = ArrayUtils.posMin(blueslope);
		int posmax2 = ArrayUtils.posMax(orangeslope);
		int posmin2 = ArrayUtils.posMin(orangeslope);
		Plot plot3 = new Plot("slopes", "pixel", "valore");

		if (step) {
			// plot3.setColor(Color.blue);
			// plot3.addPoints(vet2X, blueslope, Plot.LINE);
			plot3.setColor(Color.orange);
			plot3.addPoints(vet2X, orangeslope, Plot.LINE);
			plot3.setLimitsToFit(updateImg);
			plot3.show();
			MyLog.waitHere("ANGOLO");
		}

		double soglia1 = 0.5;
		boolean reverse = true;
		int uno = soglia(blueslope, soglia1, posmax1, reverse);
		reverse = false;
		int due = soglia(blueslope, soglia1, posmin1, reverse);

		reverse = true;
		int tre = soglia(orangeslope, soglia1, posmax2, reverse);
		reverse = false;
		int quattro = soglia(orangeslope, soglia1, posmin2, reverse);
		if (step)
			MyLog.waitHere("uno= " + uno + " due= " + due + " tre= " + tre + " quattro= " + quattro);

		double[] xpoints1 = new double[2];
		double[] ypoints1 = new double[2];
		xpoints1[0] = uno;
		xpoints1[1] = due;
		ypoints1[0] = blueslope[uno];
		ypoints1[1] = blueslope[due];

		double[] xpoints2 = new double[2];
		double[] ypoints2 = new double[2];
		xpoints2[0] = tre;
		xpoints2[1] = quattro;
		ypoints2[0] = orangeslope[tre];
		ypoints2[1] = orangeslope[quattro];

		if (step) {
			plot3.setColor(Color.blue);
			plot3.addPoints(xpoints1, ypoints1, Plot.CIRCLE);
			plot3.setColor(Color.orange);
			plot3.addPoints(xpoints2, ypoints2, Plot.CIRCLE);
			plot3.setLimitsToFit(updateImg);
			plot3.updateImage();
			MyLog.waitHere("AGGIUNTA PUNTI");
		}

		// ottenuti i punti uno e due effettuo l'interpolazione dei dati ivi
		// compresi

		double[] yprofile3 = interpolazione(erf, uno, due);
		double[] xprofile3 = new double[yprofile3.length];
		for (int i1 = 0; i1 < yprofile3.length; i1++) {
			xprofile3[i1] = i1;
		}

		if (step) {
			Plot plot4 = new Plot("erf restante", "pixel", "valore");
			plot4.setColor(Color.orange);
			plot4.addPoints(vet2X, erf, Plot.LINE);
			plot4.setColor(Color.green);
			plot4.addPoints(xprofile3, yprofile3, Plot.X);
			plot4.setLimitsToFit(updateImg);
			plot4.show();
			MyLog.waitHere("ERF COMPARATE");
		}

		if (invert) {
			for (int i1 = 0; i1 < yprofile3.length; i1++) {
				yprofile3[i1] *= (-1);
			}
		}

		if (step) {
			Plot plot4a = new Plot("erf restante", "pixel", "valore");
			plot4a.setColor(Color.green);
			plot4a.addPoints(xprofile3, yprofile3, Plot.LINE);
			plot4a.setLimitsToFit(updateImg);
			plot4a.show();
			MyLog.waitHere("ERF RESTANTE");
		}

		return (yprofile3);
	} // createErf

	/**
	 * display di un profilo con linea a met� altezza
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
	public void createPlot2(double[] profile1, boolean bslab, double dimPixel, boolean bLabelSx, String sTitolo,
			boolean bFw) {
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
		double fwhm = (dx - sx) * dimPixel * Math.tan(Math.toRadians(11));
		if (bLabelSx)
			labPos = 0.10;
		else
			labPos = 0.60;
		if (bFw) {
			plot.addLabel(labPos, 0.45, "peak / 2=   " + IJ.d2s(max1 / 2, 2));
			plot.addLabel(labPos, 0.50, "down sx " + isd2[0] + "  =   " + IJ.d2s(profile1[isd2[0]], 2));
			plot.addLabel(labPos, 0.55, "down dx " + isd2[2] + "  =   " + IJ.d2s(profile1[isd2[2]], 2));
			plot.addLabel(labPos, 0.60, "up      sx " + isd2[1] + "  =   " + IJ.d2s(profile1[isd2[1]], 2));
			plot.addLabel(labPos, 0.65, "up      dx " + isd2[3] + "  =   " + IJ.d2s(profile1[isd2[3]], 2));
			plot.addLabel(labPos, 0.70, "sx interp       =  " + IJ.d2s(sx, 2));
			plot.addLabel(labPos, 0.75, "dx interp       =  " + IJ.d2s(dx, 2));
			plot.addLabel(labPos, 0.80, "fwhm            =  " + IJ.d2s(fwhm, 2) + " mm");
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
	 * analisi di un profilo normale con ricerca punti sopra e sotto met� altezza
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

		// calcolo met� altezza
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
	 * inclinazione e tilt (cio' che veniva effettuato dal foglio Excel)
	 * 
	 * @param R1
	 * @param R2
	 * @param sTeor
	 *            spessore teorico
	 * @return spessore dello strato
	 */
	public double[] spessStrato(double R1, double R2, double sTeor, double dimPix2) {

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
	 * valori assurdi, crea un area al di fuori della quale il valore memorizzato e'
	 * quello di default, inoltre non permette che il segmento sia piu' corto di 10
	 * pixel
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
		double len = Math.sqrt(Math.pow(vetLine[0] - vetLine[2], 2) + Math.pow(vetLine[1] - vetLine[3], 2));
		if (len < 10)
			return true;
		// se viene ribaltato il segmento interviene
		if (vetLine[1] > vetLine[3])
			return true;

		return false;
	}

	void errorlog(double[] profile1, double soglia, int start, boolean reverse) {

		double[] vet2X = new double[profile1.length];
		for (int i1 = 0; i1 < profile1.length; i1++) {
			vet2X[i1] = i1;
		}
		double[] xx = new double[1];
		xx[0] = start;
		double[] yy = new double[1];
		yy[0] = profile1[start];
		double[] sogliax = new double[2];
		sogliax[0] = 0;
		sogliax[0] = profile1.length - 1;
		double[] sogliay = new double[2];
		sogliay[0] = soglia;
		sogliay[0] = soglia;

		Plot plot6 = new Plot("ERRORE", "pixel", "valore");
		plot6.setColor(Color.blue);
		plot6.addPoints(vet2X, profile1, Plot.LINE);
		plot6.setColor(Color.blue);
		plot6.addPoints(xx, yy, Plot.CIRCLE);
		plot6.setColor(Color.green);
		plot6.addPoints(sogliax, sogliay, Plot.LINE);
		plot6.addLegend("segnale\nstart\nsoglia1");
		plot6.setLimitsToFit(true);
		plot6.show();
		MyLog.waitThere("ERRORE soglia= " + soglia + " start= " + start + " reverse= " + reverse);

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
		double peak_cuneo4 = 64.0625;
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
		double[] vetReference = { slicePos, fwhmSlab1, peak_slab1, fwhm_slab2, peak_slab2, fwhm_cuneo3, peak_cuneo3,
				fwhm_cuneo4, peak_cuneo4, S1CorSlab, S2CorSlab, ErrSperSlab, AccurSpesSlab, S1CorCuneo, S2CorCuneo,
				ErrSperCuneo, AccurSpesCuneo, Accettab, DimPix, Thick, Spacing };
		return vetReference;
	}

	/**
	 * Restituisce i valori di riferimento per le immagini Ge (e simili), i valori
	 * non dovrebbero mai cambiare, salvo che per modifiche a ImageJ
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
		double peak_cuneo4 = 77.3447413444519;
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

		double[] vetReference = { slicePos, fwhmSlab1, peak_slab1, fwhm_slab2, peak_slab2, fwhm_cuneo3, peak_cuneo3,
				fwhm_cuneo4, peak_cuneo4, S1CorSlab, S2CorSlab, ErrSperSlab, AccurSpesSlab, S1CorCuneo, S2CorCuneo,
				ErrSperCuneo, AccurSpesCuneo, Accettab, DimPix, Thick, Spacing };
		return vetReference;
	}

	public static void msgSquare() {
		ButtonMessages.ModelessMsg("Far coincidere il segmento  con il lato sx del quadrato", "CONTINUA");
	}

	public static void msgSquareCoordinates(double[] vetReference) {
		ButtonMessages.ModelessMsg("coordinate posizionamento ax= " + vetReference[0] + "   ay = " + vetReference[1]
				+ "   bx = " + vetReference[2] + "  by = " + vetReference[3], "CONTINUA");
	}

	public static int msgAccept() {
		int userSelection = ButtonMessages.ModelessMsg("Accettabilita' immagine   <08>", "ELABORA", "SALTA");
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
		ButtonMessages.ModelessMsg("Profilo mediato e baseline correction", "CONTINUA");
	}

	public static void msgFwhm() {
		ButtonMessages.ModelessMsg("Profilo mediato + baseline + FWHM", "CONTINUA");
	}

	public static void msgErf() {
		ButtonMessages.ModelessMsg("Profilo ERF + smooth 3x3 + FWHM", "CONTINUA");
	}

}
