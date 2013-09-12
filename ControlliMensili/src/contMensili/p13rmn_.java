package contMensili;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.gui.Line;
import ij.gui.NewImage;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.TextRoi;
import ij.gui.WaitForUserDialog;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.frame.Editor;
import ij.process.FloatPolygon;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;
import ij.util.Tools;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Vector;

import org.w3c.dom.css.Rect;

import utils.AboutBox;
import utils.ArrayUtils;
import utils.ButtonMessages;
import utils.ImageUtils;
import utils.InputOutput;
import utils.MyMsg;
import utils.MyConst;
import utils.MyFwhm;
import utils.MyLine;
import utils.MyLog;
import utils.MyPlot;
import utils.MyVersionUtils;
import utils.ReadDicom;
import utils.ReadVersion;
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
 * Lettura del valore della frequenza
 * 
 * +++++++++++++++++ MA A ME SEMBRA CHE FUNZIONI ++++++++++++++++++++++
 * 
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 * 
 */
public class p13rmn_ implements PlugIn, Measurements {

	private static final int ABORT = 1;

	public static String VERSION = "p13_rmn_v1.10_12_set_2013_";

	private String TYPE = " >> LETTURA FREQUENZA";

	// private static String simulataName = "";

	/**
	 * directory dati, dove vengono memorizzati ayv.txt e Results1.txt
	 */
	private static String fileDir = "";

	private static boolean previous = false;
	private static boolean init1 = true;
	@SuppressWarnings("unused")
	private static boolean pulse = false; // lasciare, serve anche se segnalato
											// inutilizzato

	public void run(String args) {

		Count c1 = new Count();
		if (!c1.jarCount("iw2ayv_"))
			return;

		String className = this.getClass().getName();

		VERSION = className + "_build_" + MyVersion.getVersion()
				+ "_iw2ayv_build_" + MyVersionUtils.getVersion();

		fileDir = Prefs.get("prefer.string1", "none");

		if (IJ.versionLessThan("1.43k"))
			return;
		
		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir
				+ MyConst.SEQUENZE_FILE);
		
		int[] vetRiga = UtilAyv.decodeTokens(args);

		String path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);

		
		ImagePlus imp1 = UtilAyv.openImageNoDisplay(path1, true);
		if (imp1 == null)
			MyLog.waitHere("Non trovato il file " + path1);
		//
		String[][] tabCodici = TableCode.loadTableCSV(MyConst.CODE_FILE);

		String[] info1 = ReportStandardInfo.getSimpleStandardInfo(
				path1, imp1, tabCodici, VERSION, false);

		ResultsTable rt = ReportStandardInfo.putSimpleStandardInfoRT(info1);
		
		double frequenza = ReadDicom.readDouble(ReadDicom.readDicomParameter(imp1,
				MyConst.DICOM_IMAGING_FREQUENCY));

		
		int col = 2;
		String t1 = "TESTO          ";
		// put values in ResultsTable
		rt = ReportStandardInfo.putSimpleStandardInfoRT(info1);
		rt.setHeading(++col, "roi_x");
		rt.setHeading(++col, "roi_y");
		rt.setHeading(++col, "roi_b");
		rt.setHeading(++col, "roi_h");

		rt.addLabel(t1, "Frequenza");
		rt.addValue(2, frequenza);
		rt.addValue(3, 0);
		rt.addValue(4, 0);
		rt.addValue(5, 0);
		rt.addValue(6, 0);

		UtilAyv.saveResults3(vetRiga, fileDir, iw2ayvTable);

		return;
	}



}
