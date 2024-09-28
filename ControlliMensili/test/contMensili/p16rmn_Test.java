package contMensili;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.util.ArrayList;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import utils.InputOutput;
import utils.MyConst;
import utils.MyLog;
import utils.MyPlot;
import utils.ReadDicom;
import utils.UtilAyv;

public class p16rmn_Test {

	@Before
	public void setUp() throws Exception {
		new ImageJ(ImageJ.NORMAL);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testVersion() {
		assertFalse(IJ.versionLessThan("1.42p"));
	}

	@Test
	public final void testRegressor() {
		
		ArrayList<Double> arrMeanNorm= new ArrayList<Double>();
		ArrayList<Double> arrBvalue= new ArrayList<Double>();
		ArrayList<String> arrGradient=new ArrayList<String>();
		

		
		arrMeanNorm.add(1.);
		arrBvalue.add(0.);
		arrGradient.add("x");
		
		arrMeanNorm.add(1.);
		arrBvalue.add(0.);
		arrGradient.add("y");
		
		arrMeanNorm.add(1.);
		arrBvalue.add(0.);
		arrGradient.add("z");

		// 1	0	0
		arrMeanNorm.add(0.350359);
		arrBvalue.add(500.);
		arrGradient.add("x");
		
		arrMeanNorm.add(0.356755);
		arrBvalue.add(500.);
		arrGradient.add("y");
		
		arrMeanNorm.add(0.355772);
		arrBvalue.add(500.);
		arrGradient.add("z");
		
		arrMeanNorm.add(0.122974);
		arrBvalue.add(1000.);
		arrGradient.add("x");
		
		arrMeanNorm.add(0.127115);
		arrBvalue.add(1000.);
		arrGradient.add("y");
		
		arrMeanNorm.add(0.125856);
		arrBvalue.add(1000.);
		arrGradient.add("z");
		
		arrMeanNorm.add(0.042999);
		arrBvalue.add(1500.);
		arrGradient.add("x");

		arrMeanNorm.add(0.044948);
		arrBvalue.add(1500.);
		arrGradient.add("y");

		arrMeanNorm.add(0.044197);
		arrBvalue.add(1500.);
		arrGradient.add("z");

		arrMeanNorm.add(0.014957);
		arrBvalue.add(2000.);
		arrGradient.add("x");

		arrMeanNorm.add(0.015697);
		arrBvalue.add(2000.);
		arrGradient.add("y");

		arrMeanNorm.add(0.015366);
		arrBvalue.add(2000.);
		arrGradient.add("z");

		arrMeanNorm.add(0.005434);
		arrBvalue.add(2500.);
		arrGradient.add("x");

		arrMeanNorm.add(0.005811);
		arrBvalue.add(2500.);
		arrGradient.add("y");

		arrMeanNorm.add(0.005952);
		arrBvalue.add(2500.);
		arrGradient.add("z");

		arrMeanNorm.add(0.003401);
		arrBvalue.add(3000.);
		arrGradient.add("x");

		arrMeanNorm.add(0.003193);
		arrBvalue.add(3000.);
		arrGradient.add("y");

		arrMeanNorm.add(0.003263);
		arrBvalue.add(3000.);
		arrGradient.add("z");

		double[] out2= p16rmn_.regressor(arrMeanNorm, arrBvalue,
				arrGradient);

	}

}