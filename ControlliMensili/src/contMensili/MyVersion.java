package contMensili;


	public class MyVersion {
		public final static String CURRENT_VERSION = MyVersion.class.getPackage().getImplementationVersion();
		
		public static String getVersion() {
			String out1;
			if (CURRENT_VERSION == null)
				out1 = "UNKNOWN";
			else
				out1 = CURRENT_VERSION;

			return out1;
		}

	}

