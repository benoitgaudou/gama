/*********************************************************************************************
 *
 * 'GamaPreferences.java, in plugin msi.gama.core, is part of the source code of the GAMA modeling and simulation
 * platform. (c) 2007-2016 UMI 209 UMMISCO IRD/UPMC & Partners
 *
 * Visit https://github.com/gama-platform/gama for license information and developers contact.
 * 
 *
 **********************************************************************************************/
package msi.gama.common;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.prefs.BackingStoreException;
import java.util.prefs.InvalidPreferencesFormatException;
import java.util.prefs.Preferences;

import org.geotools.referencing.CRS;

import com.vividsolutions.jts.geom.Envelope;

import gnu.trove.map.hash.THashMap;
import msi.gama.common.interfaces.IKeyword;
import msi.gama.common.util.FileUtils;
import msi.gama.common.util.StringUtils;
import msi.gama.kernel.experiment.IParameter;
import msi.gama.runtime.GAMA;
import msi.gama.runtime.IScope;
import msi.gama.runtime.exceptions.GamaRuntimeException;
import msi.gama.util.GamaColor;
import msi.gama.util.GamaDate;
import msi.gama.util.GamaListFactory;
import msi.gama.util.IList;
import msi.gama.util.TOrderedHashMap;
import msi.gama.util.file.GamaFile;
import msi.gama.util.file.IGamaFile;
import msi.gaml.compilation.kernel.GamaMetaModel;
import msi.gaml.descriptions.PlatformSpeciesDescription;
import msi.gaml.operators.Cast;
import msi.gaml.operators.Strings;
import msi.gaml.statements.Facets;
import msi.gaml.types.GamaBoolType;
import msi.gaml.types.GamaFloatType;
import msi.gaml.types.GamaFontType;
import msi.gaml.types.GamaIntegerType;
import msi.gaml.types.GamaStringType;
import msi.gaml.types.IContainerType;
import msi.gaml.types.IType;
import msi.gaml.types.Types;

/**
 * Class GamaPreferencesView.
 *
 * @author drogoul
 * @since 26 août 2013
 *
 */
@SuppressWarnings ({ "unchecked", "rawtypes" })
public class GamaPreferences {

	public static final String UI = "Presentation";
	public static final String EXPERIMENTS = "Experiments";
	public static final String SIMULATIONS = "Simulations";
	public static final String CONCURRENCY = "Concurrency";
	public static final String EXPERIMENTAL = "Performances";
	public static final String DISPLAY = "Displays";
	public static final String EDITOR = "Editors";
	public static final String LIBRARIES = "External";
	static Preferences store;
	private static Map<String, Entry<? extends Object>> prefs = new LinkedHashMap<>();
	private static List<String> storeKeys;

	static {

		try {
			store = Preferences.userRoot().node("gama");
			try {
				store.flush();
			} catch (final BackingStoreException e1) {
				e1.printStackTrace();
			}
			try {
				storeKeys = Arrays.asList(store.keys());
			} catch (final BackingStoreException e) {
				e.printStackTrace();
			}
		} catch (final RuntimeException e) {
			e.printStackTrace();
		}
	}

	public static <T> Entry<T> get(final String key, final Class<T> clazz) {
		return (Entry<T>) prefs.get(key);
	}

	public static Entry<?> get(final String key) {
		return prefs.get(key);
	}

	public static Map<String, Entry<?>> getAll() {
		return prefs;
	}

	public static <T> Entry<T> create(final String key, final String title, final T value, final int type) {
		if (key.contains(".") || key.contains(" "))
			System.out.println("WARNING. Preference " + key + " cannot be used as a variable");
		final Entry<T> e = new Entry<T>(key, type).named(title).in(UI).init(value);
		register(e);
		return e;
	}

	public static interface IPreferenceChangeListener<T> {

		/**
		 * A change listener, that receives the beforeValueChange() message before the preference is assigned a new
		 * value, with this value in parameter. Returning true will enable the change, returning false will veto it.
		 * 
		 * @param newValue,
		 *            the new value set to this preference
		 * @return true or false, whether or not the change is accepted by the listener.
		 */
		public boolean beforeValueChange(T newValue);

		/**
		 * A change listener, that receives the afterValueChange() message after the preference is assigned a new value,
		 * with this value in parameter, in order to perform anything needed for this change.
		 * 
		 * @param newValue,
		 *            the new value set to this preference
		 * @return true or false, whether or not the change is accepted by the listener.
		 */
		public void afterValueChange(T newValue);
	}

	public static class GenericFile extends GamaFile implements IGamaFile {

		private boolean shouldExist;

		public GenericFile(final String pathName) throws GamaRuntimeException {
			super(null, pathName);
		}

		public GenericFile(final String pathName, final boolean shouldExist) {
			this(pathName);
			this.shouldExist = shouldExist;
		}

		@Override
		public boolean shouldExist() {
			if (shouldExist)
				return super.shouldExist();
			return false;
		}

		public GenericFile(final IScope scope, final String pathName) throws GamaRuntimeException {
			super(scope, pathName);
		}

		@Override
		public IContainerType getType() {
			return Types.FILE;
		}

		@Override
		public Envelope computeEnvelope(final IScope scope) {
			return new Envelope(0, 0, 0, 0);
		}

		@Override
		protected void fillBuffer(final IScope scope) throws GamaRuntimeException {
			if (getBuffer() != null) { return; }
			if (FileUtils.isBinaryFile(scope, getFile(scope))) {
				GAMA.reportAndThrowIfNeeded(scope,
						GamaRuntimeException.warning(
								"Problem identifying the contents of " + getFile(scope).getAbsolutePath(), scope),
						false);
				setBuffer(GamaListFactory.create());
			}
			try {
				final BufferedReader in = new BufferedReader(new FileReader(getFile(scope)));
				final IList<String> allLines = GamaListFactory.create(Types.STRING);
				String str;
				str = in.readLine();
				while (str != null) {
					allLines.add(str);
					str = in.readLine();
				}
				in.close();
				setBuffer(allLines);
			} catch (final IOException e) {
				throw GamaRuntimeException.create(e, scope);
			}

		}

		@Override
		protected void flushBuffer(final IScope scope, final Facets facets) throws GamaRuntimeException {}

	}

	public static class Entry<T> implements IParameter {

		String key, title, tab, group;
		T value, initial;
		final int type;
		List<T> values;
		Number min, max;
		boolean slider = true; // by default
		String[] activates, deactivates;
		Set<IPreferenceChangeListener<T>> listeners = new HashSet<IPreferenceChangeListener<T>>();

		Entry(final String key, final int type) {
			tab = UI;
			this.type = type;
			this.key = key;
		}

		public Entry<T> group(final String g) {
			this.group = g;
			return this;
		}

		public Entry<T> onChange(final Consumer<T> consumer) {
			addChangeListener(new IPreferenceChangeListener<T>() {

				@Override
				public boolean beforeValueChange(final T newValue) {
					return true;
				}

				@Override
				public void afterValueChange(final T newValue) {
					consumer.accept(newValue);

				}
			});
			return this;
		}

		public Entry<T> noSlider() {
			slider = false;
			return this;
		}

		public Entry<T> among(final T... v) {
			return among(Arrays.asList(v));
		}

		public Entry<T> among(final List<T> v) {
			this.values = v;
			return this;
		}

		public Entry<T> between(final Number mini, final Number maxi) {
			this.min = mini;
			this.max = maxi;
			return this;
		}

		public Entry<T> in(final String category) {
			this.tab = category;
			return this;
		}

		public Entry<T> named(final String t) {
			this.title = t;
			return this;
		}

		public Entry<T> init(final T v) {
			this.initial = v;
			this.value = v;
			return this;
		}

		public Entry<T> set(final T value) {
			if (isValueChanged(value) && acceptChange(value)) {
				this.value = value;
				afterChange(value);
			}
			return this;
		}

		private boolean isValueChanged(final T newValue) {
			return value == null ? newValue != null : !value.equals(newValue);
		}

		public Entry<T> activates(final String... link) {
			activates = link;
			return this;
		}

		public Entry<T> deactivates(final String... link) {
			deactivates = link;
			return this;
		}

		public T getValue() {
			return value;
		}

		@Override
		public IType<?> getType() {
			return Types.get(type);
		}

		@Override
		public String getTitle() {
			return title;
		}

		public String getKey() {
			return key;
		}

		public List<T> getValues() {
			return values;
		}

		@Override
		public String getName() {
			return key;
		}

		@Override
		public String getCategory() {
			return group;
		}

		@Override
		public String getUnitLabel(final IScope scope) {
			return null;
		}

		@Override
		public void setUnitLabel(final String label) {}

		@Override
		public void setValue(final IScope scope, final Object value) {
			set((T) value);
		}

		public Entry<T> addChangeListener(final IPreferenceChangeListener<T> r) {
			listeners.add(r);
			return this;
		}

		public void removeChangeListener(final IPreferenceChangeListener<T> r) {
			listeners.remove(r);
		}

		@Override
		public T value(final IScope scope) throws GamaRuntimeException {
			return getValue();
		}

		@Override
		public String serialize(final boolean includingBuiltIn) {
			return StringUtils.toGaml(value, includingBuiltIn);
		}

		@Override
		public Object getInitialValue(final IScope scope) {
			return initial;
		}

		@Override
		public Number getMinValue(final IScope scope) {
			return min;
		}

		@Override
		public Number getMaxValue(final IScope scope) {
			return max;
		}

		@Override
		public List getAmongValue(final IScope scope) {
			return values;
		}

		@Override
		public boolean isEditable() {
			return true;
		}

		@Override
		public boolean isDefined() {
			return true;
		}

		@Override
		public void setDefined(final boolean b) {}

		@Override
		public Number getStepValue(final IScope scope) {
			return null;
		}

		/**
		 * If the value is modified, this method is called. Should return true to accept the change, false otherwise
		 */
		public boolean acceptChange(final T newValue) {
			for (final IPreferenceChangeListener<T> listener : listeners) {
				if (!listener.beforeValueChange(newValue)) { return false; }
			}
			return true;
		}

		protected void afterChange(final T newValue) {
			for (final IPreferenceChangeListener<T> listener : listeners) {
				listener.afterValueChange(newValue);
			}
		}

		public String[] getActivable() {
			return this.activates;
		}

		public String[] getDeactivable() {
			return this.deactivates;
		}

		public void save() {
			final Map<String, Object> map = new THashMap<>();
			map.put(getName(), getValue());
			GamaPreferences.setNewPreferences(map);
		}

		@Override
		public boolean acceptsSlider(final IScope scope) {
			return slider;
		}
	}

	/**
	 * Definition of the preferences contributed by msi.gama.core
	 */

	// GENERAL PAGE
	public static final List<String> GENERATOR_NAMES =
			Arrays.asList(IKeyword.CELLULAR, IKeyword.JAVA, IKeyword.MERSENNE);
	/**
	 * User Interface
	 */
	public static final Entry<Integer> CORE_MENU_SIZE =
			create("pref_menu_size", "Break down agents in menus every", 50, IType.INT).between(10, 100).in(UI)
					.group("Menus");

	public static final Entry<Integer> CORE_CONSOLE_SIZE =
			create("pref_console_size", "Max. number of characters to display in the console (-1 means no limit) ",
					20000, IType.INT).in(UI).group("Console");
	public static final Entry<Integer> CORE_CONSOLE_BUFFER = create("pref_console_buffer",
			"Max. number of characters to keep in memory when console is paused (-1 means no limit)", 20000, IType.INT)
					.in(UI).group("Console");
	public static final Entry<Boolean> CORE_CONSOLE_WRAP =
			create("pref_console_wrap", "Automatically wrap long lines in the console (can slow down output)", false,
					IType.BOOL).in(UI).group("Console");
	// EDITOR PAGE
	public static final Entry<Boolean> CORE_PERSPECTIVE =
			create("pref_switch_perspective", "Automatically switch to modeling perspective when editing a model",
					false, IType.BOOL).in(EDITOR).group("Options");
	public static final Entry<Boolean> CORE_SIMULATION_NAME = create("pref_append_simulation_name",
			"Append the name of the simulation to the title of its outputs", false, IType.BOOL).in(UI).group("Options");

	/**
	 * Validation
	 */
	public static final Entry<Boolean> WARNINGS_ENABLED = GamaPreferences
			.create("pref_editor_enable_warnings", "Show warning markers when editing a model", true, IType.BOOL)
			.in(EDITOR).group("Validation");

	public static final Entry<Boolean> INFO_ENABLED = GamaPreferences
			.create("pref_editor_enable_infos", "Show information markers when editing a model", true, IType.BOOL)
			.in(EDITOR).group("Validation");
	/**
	 * Random Number Generation
	 */
	public static final Entry<String> CORE_RNG =
			create("pref_rng_name", "Random number generator", IKeyword.MERSENNE, IType.STRING).among(GENERATOR_NAMES)
					.in(EXPERIMENTS).group("Random Number Generation");
	public static final Entry<Boolean> CORE_SEED_DEFINED =
			create("pref_rng_define_seed", "Define a default seed", false, IType.BOOL)
					.activates("pref_rng_default_seed").in(EXPERIMENTS).group("Random Number Generation");
	public static final Entry<Double> CORE_SEED =
			create("pref_rng_default_seed", "Default seed value (0 means undefined)", 1d, IType.FLOAT).in(EXPERIMENTS)
					.group("Random Number Generation");
	public static final Entry<Boolean> CORE_RND_EDITABLE =
			create("pref_rng_in_parameters", "Include in the parameters of models", true, IType.BOOL).in(EXPERIMENTS)
					.group("Random Number Generation");

	/**
	 * Simulation Errors
	 */
	public static final Entry<Boolean> CORE_SHOW_ERRORS =
			create("pref_errors_display", "Display errors", true, IType.BOOL).in(EXPERIMENTS)
					.activates("pref_errors_number", "core.recent").group("Errors");
	public static final Entry<Integer> CORE_ERRORS_NUMBER =
			create("pref_errors_number", "Number of errors to display", 10, IType.INT).in(EXPERIMENTS).group("Errors")
					.between(1, null);
	public static final Entry<Boolean> CORE_RECENT =
			create("pref_errors_recent_first", "Display most recent first", true, IType.BOOL).in(EXPERIMENTS)
					.group("Errors");
	public static final Entry<Boolean> CORE_REVEAL_AND_STOP =
			create("pref_errors_stop", "Stop simulation at first error", true, IType.BOOL).in(EXPERIMENTS)
					.group("Errors");
	public static final Entry<Boolean> CORE_WARNINGS =
			create("pref_errors_warnings_errors", "Treat warnings as errors", false, IType.BOOL).in(EXPERIMENTS)
					.group("Errors");
	/**
	 * Startup
	 */
	public static final Entry<Boolean> CORE_SHOW_PAGE =
			create("pref_show_welcome_page", "Display Welcome page at startup", true, IType.BOOL).in(UI)
					.group("Startup");

	public static final Entry<Boolean> CORE_SHOW_MAXIMIZED =
			create("pref_show_maximized", "Maximize GAMA windows at startup", true, IType.BOOL).in(UI).group("Startup");
	/**
	 * Runtime
	 */
	public static final Entry<Double> CORE_DELAY_STEP =
			create("pref_experiment_default_step", "Default step for delay slider (in sec.)", 0.01, IType.FLOAT)
					.in(EXPERIMENTS).group("Runtime");
	public static final Entry<Boolean> CORE_AUTO_RUN =
			create("pref_experiment_auto_run", "Auto-run experiments when they are launched", false, IType.BOOL)
					.in(EXPERIMENTS).group("Runtime");
	public static final Entry<Boolean> CORE_ASK_CLOSING =
			create("pref_experiment_ask_closing", "Ask to close the previous experiment before launching a new one ?",
					true, IType.BOOL).in(EXPERIMENTS).group("Runtime");

	public static final GamaColor[] BASIC_COLORS =
			new GamaColor[] { new GamaColor(74, 97, 144), new GamaColor(66, 119, 42), new GamaColor(83, 95, 107),
					new GamaColor(195, 98, 43), new GamaColor(150, 132, 106) };
	public static final Entry<GamaColor>[] SIMULATION_COLORS = new Entry[5];

	static {
		for (int i = 0; i < 5; i++) {
			SIMULATION_COLORS[i] =
					create("pref_simulation_color_" + i, "Color of Simulation " + i, BASIC_COLORS[i], IType.COLOR)
							.in(EXPERIMENTS).group("Simulation colors in the interface (console, views)");
		}
	}

	// DISPLAY PAGE
	/**
	 * Properties
	 */
	public static final Entry<Boolean> CORE_DISPLAY_PERSPECTIVE = create("pref_display_continue_drawing",
			"Continue to draw displays (in the background) when in Modeling perspective", false, IType.BOOL).in(DISPLAY)
					.group("Behavior of displays");
	public static final Entry<Boolean> DISPLAY_MODAL_FULLSCREEN =
			create("pref_display_fullscreen_menu", "Disable the menu bar when displays are turned full-screen", true,
					IType.BOOL).in(DISPLAY).group("Behavior of displays");
	public static final Entry<String> CORE_DISPLAY =
			create("pref_display_default", "Default display method when none is specified", "Java2D", IType.STRING)
					.among("Java2D", "OpenGL").in(DISPLAY)
					.group("Properties (settings effective after experiment relaunch)");
	public static final Entry<Boolean> CORE_SYNC =
			create("pref_display_synchronized", "Synchronize displays with simulations", false, IType.BOOL).in(DISPLAY)
					.group("Properties (settings effective after experiment relaunch)");
	public static final Entry<Boolean> CORE_OVERLAY =
			create("pref_display_show_overlay", "Show display overlay", false, IType.BOOL).in(DISPLAY)
					.activates("pref_display_show_scale", "pref_display_show_fps")
					.group("Properties (settings effective after experiment relaunch)");
	public static final Entry<Boolean> CORE_SCALE =
			create("pref_display_show_scale", "Show scale bar in overlay", false, IType.BOOL).in(DISPLAY)
					.group("Properties (settings effective after experiment relaunch)");
	public static final Entry<Boolean> CORE_SHOW_FPS =
			create("pref_display_show_fps", "Show number of frames per second in overlay", false, IType.BOOL)
					.in(DISPLAY).group("Properties (settings effective after experiment relaunch)");
	public static final Entry<Boolean> CHART_FLAT =
			create("pref_display_flat_charts", "Display histograms with a 'flat' look", false, IType.BOOL).in(DISPLAY)
					.group("Properties (settings effective after experiment relaunch)");

	public static final Entry<Boolean> CORE_ANTIALIAS =
			create("pref_display_antialias", "Apply antialiasing", false, IType.BOOL).in(DISPLAY)
					.group("Properties (settings effective after experiment relaunch)");
	public static final Entry<GamaColor> CORE_BACKGROUND =
			create("pref_display_background_color", "Default background color", GamaColor.getNamed("white"),
					IType.COLOR).in(DISPLAY).group("Properties (settings effective after experiment relaunch)");
	public static final Entry<GamaColor> CORE_HIGHLIGHT =
			create("pref_display_highlight_color", "Default highlight color", new GamaColor(0, 200, 200), IType.COLOR)
					.in(DISPLAY).group("Properties (settings effective after experiment relaunch)");
	public static final Entry<Boolean> CORE_DISPLAY_ORDER =
			create("pref_display_same_order", "Stack displays on screen in the order defined by the model", true,
					IType.BOOL).in(DISPLAY).group("Layout (settings effective after experiment relaunch)");
	public static final Entry<Boolean> CORE_DISPLAY_BORDER =
			create("pref_display_show_border", "Display a border around the view", true, IType.BOOL).in(DISPLAY)
					.group("Properties (settings effective after experiment relaunch)");

	public static final List<String> LAYOUTS = Arrays.asList("None", "Stacked", "Split", "Horizontal", "Vertical");
	public static final Entry<String> CORE_DISPLAY_LAYOUT =
			create("pref_display_view_layout", "Default layout of display views", "None", IType.STRING)
					.among(LAYOUTS.toArray(new String[0])).in(DISPLAY)
					.group("Layout (settings effective after experiment relaunch)");

	/**
	 * Default Aspect
	 */
	public static final Entry<String> CORE_SHAPE =
			create("pref_display_default_shape", "Defaut shape to use for agents", "shape", IType.STRING)
					.among("shape", "circle", "square", "triangle", "point", "cube", "sphere").in(DISPLAY)
					.group("Default aspect (settings effective after experiment relaunch)");
	public static final Entry<Double> CORE_SIZE =
			create("pref_display_default_size", "Default size to use for agents", 1.0, IType.FLOAT).between(0.01, null)
					.in(DISPLAY).group("Default aspect (settings effective after experiment relaunch)");
	public static final Entry<GamaColor> CORE_COLOR =
			create("pref_display_default_color", "Default color to use for agents", GamaColor.getNamed("yellow"),
					IType.COLOR).in(DISPLAY).group("Default aspect (settings effective after experiment relaunch)");
	/**
	 * OpenGL
	 */
	public static final Entry<Boolean> CORE_DRAW_ENV =
			create("pref_display_show_referential", "Draw 3D referential", true, IType.BOOL).in(DISPLAY)
					.group("OpenGL (settings effective after experiment relaunch)");
	public static final Entry<Boolean> DRAW_ROTATE_HELPER =
			create("pref_display_show_rotation", "Draw rotation helper", true, IType.BOOL).in(DISPLAY)
					.group("OpenGL (settings effective after experiment relaunch)");

	public static final Entry<Double> OPENGL_ZOOM = create("pref_display_zoom_factor",
			"Set the zoom factor to use (from 0 for a slow zoom to 1 for a fast one)", 0.5, IType.FLOAT).in(DISPLAY)
					.group("OpenGL (settings effective immediately)").between(0, 1);
	public static final Entry<Integer> OPENGL_FPS =
			create("pref_display_max_fps", "Set the maximum number of frames per second to display", 20, IType.INT)
					.in(DISPLAY).group("OpenGL (settings effective after experiment relaunch)");
	public static final Entry<Double> CORE_LINE_WIDTH =
			create("pref_display_line_width", "Set the width of lines drawn on displays", 1.2d, IType.FLOAT).in(DISPLAY)
					.group("OpenGL (settings effective immediately)");

	// LIBRARIES PAGE
	/**
	 * Spatialite
	 */
	public static final Entry<? extends IGamaFile> LIB_SPATIALITE =
			create("pref_lib_spatialite", "Path to the Spatialite (see http://www.gaia-gis.it/gaia-sins/) library",
					new GenericFile("Enter path", false), IType.FILE).in(LIBRARIES).group("Paths");
	/**
	 * R
	 */
	public static final Entry<? extends IGamaFile> LIB_R =
			create("pref_lib_r", "Path to the RScript (see http://www.r-project.org) library",
					new GenericFile(getDefaultRPath(), false), IType.FILE).in(LIBRARIES).group("Paths");
	/**
	 * GeoTools
	 */
	public static final Entry<Boolean> LIB_TARGETED = create("pref_gis_auto_crs",
			"Let GAMA decide which CRS to use to project GIS data", true,
			IType.BOOL).deactivates("pref_gis_default_crs").in(LIBRARIES).group(
					"GIS Coordinate Reference Systems (see http://spatialreference.org/ref/epsg/ for EPSG codes)");
	public static final Entry<Integer> LIB_TARGET_CRS =
			create("pref_gis_default_crs", "...or use the following CRS (EPSG code)", 32648, IType.INT).in(LIBRARIES)
					.group("GIS Coordinate Reference Systems (see http://spatialreference.org/ref/epsg/ for EPSG codes)")
					.addChangeListener(new IPreferenceChangeListener<Integer>() {

						@Override
						public boolean beforeValueChange(final Integer newValue) {
							final Set<String> codes = CRS.getSupportedCodes(newValue.toString());
							if (codes.isEmpty()) { return false; }
							return true;
						}

						@Override
						public void afterValueChange(final Integer newValue) {}
					});
	public static final Entry<Boolean> LIB_PROJECTED = create("pref_gis_same_crs",
			"When no .prj file or CRS is supplied, consider GIS data to be already projected in this CRS", true,
			IType.BOOL).deactivates("pref_gis_initial_crs").in(LIBRARIES).group(
					"GIS Coordinate Reference Systems (see http://spatialreference.org/ref/epsg/ for EPSG codes)");
	public static final Entry<Integer> LIB_INITIAL_CRS =
			create("pref_gis_initial_crs", "...or use the following CRS (EPSG code)", 4326, IType.INT).in(LIBRARIES)
					.group("GIS Coordinate Reference Systems (see http://spatialreference.org/ref/epsg/ for EPSG codes)")
					.addChangeListener(new IPreferenceChangeListener<Integer>() {

						@Override
						public boolean beforeValueChange(final Integer newValue) {
							final Set<String> codes = CRS.getSupportedCodes(newValue.toString());
							if (codes.isEmpty()) { return false; }
							return true;
						}

						@Override
						public void afterValueChange(final Integer newValue) {}
					});
	public static final Entry<Boolean> LIB_USE_DEFAULT = create("pref_gis_save_crs",
			"When no CRS is provided, save the GIS data with the current CRS", true,
			IType.BOOL).deactivates("pref_gis_output_crs").in(LIBRARIES).group(
					"GIS Coordinate Reference Systems (see http://spatialreference.org/ref/epsg/ for EPSG codes)");
	public static final Entry<Integer> LIB_OUTPUT_CRS =
			create("pref_gis_output_crs", "... or use this following CRS (EPSG code)", 4326, IType.INT).in(LIBRARIES)
					.group("GIS Coordinate Reference Systems (see http://spatialreference.org/ref/epsg/ for EPSG codes)")
					.addChangeListener(new IPreferenceChangeListener<Integer>() {

						@Override
						public boolean beforeValueChange(final Integer newValue) {
							final Set<String> codes = CRS.getSupportedCodes(newValue.toString());
							if (codes.isEmpty()) { return false; }
							return true;
						}

						@Override
						public void afterValueChange(final Integer newValue) {}
					});

	private static String getDefaultRPath() {
		final String os = System.getProperty("os.name");
		final String osbit = System.getProperty("os.arch");
		if (os.startsWith("Mac")) {
			if (osbit.endsWith(
					"64")) { return "/Library/Frameworks/R.framework/Versions/2.15/Resources/bin/exec/x86_64/RScript"; }
			return "/Library/Frameworks/R.framework/Versions/2.15/Resources/bin/exec/i386/RScript";
		} else if (os.startsWith("Linux")) { return "usr/bin/RScript"; }
		if (os.startsWith("Windows")) {
			if (osbit.endsWith("64")) { return "C:\\Program Files\\R\\R-2.15.1\\bin\\x64\\Rscript.exe"; }
			return "C:\\Program Files\\R\\R-2.15.1\\bin\\Rscript.exe";
		}
		return "";
	}

	public static final Entry<Boolean> ERRORS_IN_DISPLAYS =
			create("pref_display_show_errors", "Show errors in displays and outputs", false, IType.BOOL)
					.in(EXPERIMENTAL).group(DISPLAY);
	public static final Entry<Boolean> DISPLAY_ONLY_VISIBLE = create("pref_display_visible_agents",
			"Only process for display the agents that are visible", false, IType.BOOL).in(EXPERIMENTAL).group(DISPLAY);
	public static final Entry<Boolean> DISPLAY_SHARED_CONTEXT = create("pref_display_shared_cache",
			"Enable OpenGL background loading of textures (can cause problems with some graphics cards on Linux and Windows)",
			false, IType.BOOL).in(EXPERIMENTAL).group(DISPLAY);
	public static final Entry<Boolean> DISPLAY_FAST_SNAPSHOT = create("pref_display_fast_snapshot",
			"Enable fast snapshots of displays (uncomplete when the display is obscured by other views but much faster than normal snapshots)",
			false, IType.BOOL).in(EXPERIMENTAL).group(DISPLAY);
	public static final Entry<Boolean> DISPLAY_POWER_OF_TWO = create("pref_display_power_of_2",
			"Forces the dimensions of OpenGL textures to be power of 2 (e.g. 8x8, 16x16, etc.). Necessary on some graphic cards",
			false, IType.BOOL).in(EXPERIMENTAL).group(DISPLAY);
	public static final Entry<Boolean> DISPLAY_NO_ACCELERATION = create("pref_display_no_java2d_acceleration",
			"Disable graphics hardware acceleration for Java2D displays (can be necessary on some configurations)",
			false, IType.BOOL).in(EXPERIMENTAL).group(DISPLAY);
	public static final Entry<Integer> CORE_OUTPUT_DELAY = create("pref_display_delay_views",
			"Delay (in ms) between the opening of display views (increase if you experience freezes when opening displays, esp. Java2D displays)",
			200, IType.INT).between(0, 1000).in(EXPERIMENTAL).group(DISPLAY);
	public static final Entry<Boolean> CONSTANT_OPTIMIZATION = create("pref_optimize_constant_expressions",
			"Automatically optimize constant expressions", false, IType.BOOL).in(EXPERIMENTAL).group("Compilation");
	public static final Entry<Boolean> AGENT_OPTIMIZATION =
			create("pref_optimize_agent_memory", "Automatically optimize the memory used by agents", false, IType.BOOL)
					.in(EXPERIMENTAL).group("Compilation");
	public static final Entry<Boolean> MATH_OPTIMIZATION = create("pref_optimize_math_functions",
			"Use optimized (but less accurate) arithmetic and trigonometric functions", false, IType.BOOL)
					.in(EXPERIMENTAL).group("Compilation");
	public static final Entry<Boolean> AT_DISTANCE_OPTIMIZATION =
			create("pref_optimize_at_distance", "Automatically optimize the at_distance operator", true, IType.BOOL)
					.in(EXPERIMENTAL).group("Spatial Operators");

	private static void register(final Entry gp) {
		// System.out.println("+++ Registering preference " + gp.key + " in
		// store");
		final IScope scope = null;
		final String key = gp.key;
		if (key == null) { return; }
		prefs.put(key, gp);
		Object value = gp.value;
		switch (gp.type) {
			case IType.INT:
				if (storeKeys.contains(key)) {
					gp.setValue(scope, store.getInt(key, GamaIntegerType.staticCast(scope, value, null, false)));
				} else {
					store.putInt(key, GamaIntegerType.staticCast(scope, value, null, false));
				}
				break;
			case IType.FLOAT:
				if (storeKeys.contains(key)) {
					gp.setValue(scope, store.getDouble(key, GamaFloatType.staticCast(scope, value, null, false)));
				} else {
					store.putDouble(key, GamaFloatType.staticCast(scope, value, null, false));
				}
				break;
			case IType.BOOL:
				value = GamaBoolType.staticCast(scope, value, null, false);
				if (storeKeys.contains(key)) {
					gp.setValue(scope, store.getBoolean(key, (Boolean) value));
				} else {
					store.putBoolean(key, (Boolean) value);
				}
				break;
			case IType.STRING:
				if (storeKeys.contains(key)) {
					gp.setValue(scope,
							store.get(key, StringUtils.toJavaString(GamaStringType.staticCast(scope, value, false))));
				} else {
					store.put(key, StringUtils.toJavaString(GamaStringType.staticCast(scope, value, false)));
				}
				break;
			case IType.FILE:
				if (storeKeys.contains(key)) {
					gp.setValue(scope, new GenericFile(store.get(key, ""), false));
				} else {
					store.put(key, value == null ? "" : ((IGamaFile) value).getPath(scope));
				}
				break;
			case IType.COLOR:
				// Stores the preference as an int but create a color
				if (storeKeys.contains(key)) {
					final int val = store.getInt(key, GamaIntegerType.staticCast(scope, value, null, false));
					gp.setValue(scope, GamaColor.getInt(val));
				} else {
					store.putInt(key, GamaIntegerType.staticCast(scope, value, null, false));
				}
				break;
			case IType.FONT:
				if (storeKeys.contains(key)) {
					final String val = store.get(key, GamaStringType.staticCast(scope, value, false));
					gp.setValue(scope, GamaFontType.staticCast(scope, val, false));
				} else {
					store.put(key, GamaStringType.staticCast(scope, value, false));
				}
				break;
			case IType.DATE:
				if (storeKeys.contains(key)) {
					final String val =
							StringUtils.toJavaString(store.get(key, GamaStringType.staticCast(scope, value, false)));
					gp.setValue(scope, GamaDate.fromISOString(val));
				} else {
					store.put(key, GamaStringType.staticCast(scope, value, false));
				}
				break;
			default:
				if (storeKeys.contains(key)) {
					gp.setValue(scope, store.get(key, GamaStringType.staticCast(scope, value, false)));
				} else {
					store.put(key, GamaStringType.staticCast(scope, value, false));
				}
		}
		try {
			store.flush();
		} catch (final BackingStoreException e) {
			e.printStackTrace();
		}
		// Adds the preferences to the platform species if it is already created
		final PlatformSpeciesDescription spec = GamaMetaModel.INSTANCE.getPlatformSpeciesDescription();
		if (spec != null) {
			if (!spec.hasAttribute(key)) {
				spec.addPref(key, gp);
				spec.validate();
			}
		}
		// Registers the preferences in the variable of the scope provider

	}

	public static void writeToStore(final Entry gp) {
		final String key = gp.key;
		final Object value = gp.value;
		switch (gp.type) {
			case IType.INT:
				store.putInt(key, (Integer) value);
				break;
			case IType.FLOAT:
				store.putDouble(key, (Double) value);
				break;
			case IType.BOOL:
				store.putBoolean(key, (Boolean) value);
				break;
			case IType.STRING:
				store.put(key, StringUtils.toJavaString((String) value));
				break;
			case IType.FILE:
				store.put(key, ((GamaFile) value).getPath(null));
				break;
			case IType.COLOR:
				// Stores the preference as an int but create a color
				final int code = ((GamaColor) value).getRGB();
				store.putInt(key, code);
				break;
			case IType.FONT:
				store.put(key, value.toString());
				break;
			case IType.DATE:
				final GamaDate d = (GamaDate) value;
				store.put(key, StringUtils.toJavaString(d.toISOString()));
				break;
			default:
				store.put(key, (String) value);
		}
	}

	public static Map<String, Map<String, List<Entry>>> organizePrefs() {
		final Map<String, Map<String, List<Entry>>> result = new TOrderedHashMap();
		for (final Entry e : prefs.values()) {
			final String tab = e.tab;
			Map<String, List<Entry>> groups = result.get(tab);
			if (groups == null) {
				groups = new TOrderedHashMap();
				result.put(tab, groups);
			}
			final String group = e.group;
			List<Entry> in_group = groups.get(group);
			if (in_group == null) {
				in_group = new ArrayList<>();
				groups.put(group, in_group);
			}
			in_group.add(e);
		}
		return result;
	}

	public static void setNewPreferences(final Map<String, Object> modelValues) {
		for (final String name : modelValues.keySet()) {
			final Entry e = prefs.get(name);
			if (e == null) {
				continue;
			}
			e.set(modelValues.get(name));
			writeToStore(e);
			try {
				store.flush();
			} catch (final BackingStoreException ex) {
				ex.printStackTrace();
			}
		}
	}

	/**
	 *
	 */
	public static void revertToDefaultValues(final Map<String, Object> modelValues) {
		for (final String name : modelValues.keySet()) {
			final Entry e = prefs.get(name);
			if (e == null) {
				continue;
			}
			modelValues.put(name, e.initial);
			e.set(e.initial);
		}

	}

	private static void reloadPreferences(final Map<String, Object> modelValues) {
		final List<Entry> entries = new ArrayList(prefs.values());
		for (final Entry e : entries) {
			register(e);
			modelValues.put(e.key, e.getValue());
		}
	}

	public static void applyPreferencesFrom(final String path, final Map<String, Object> modelValues) {
		// System.out.println("Apply preferences from " + path);
		try {
			final FileInputStream is = new FileInputStream(path);
			store.importPreferences(is);
			is.close();
			reloadPreferences(modelValues);
		} catch (final IOException | InvalidPreferencesFormatException e) {
			e.printStackTrace();
		}
	}

	public static void savePreferencesTo(final String path) {
		try (FileWriter os = new FileWriter(path)) {
			final List<Entry> entries = new ArrayList(prefs.values());
			final StringBuilder read = new StringBuilder(1000);
			final StringBuilder write = new StringBuilder(1000);
			for (final Entry e : entries) {
				read.append(Strings.TAB).append("//").append(e.getTitle()).append(Strings.LN);
				read.append(Strings.TAB).append("write sample(gama.").append(e.getName()).append(");")
						.append(Strings.LN).append(Strings.LN);
				write.append(Strings.TAB).append("//").append(e.getTitle()).append(Strings.LN);
				write.append(Strings.TAB).append("gama.").append(e.getName()).append(" <- ")
						.append(Cast.toGaml(e.getValue())).append(";").append(Strings.LN).append(Strings.LN);
			}
			os.append("// ").append(GAMA.VERSION).append(" Preferences saved on ")
					.append(LocalDateTime.now().toString()).append(Strings.LN).append(Strings.LN);
			os.append("model preferences").append(Strings.LN).append(Strings.LN);
			os.append("experiment 'Display Preferences' type: gui {").append(Strings.LN);
			os.append("init {").append(Strings.LN);
			os.append(read);
			os.append("}").append(Strings.LN);
			os.append("}").append(Strings.LN).append(Strings.LN).append(Strings.LN);
			os.append("experiment 'Set Preferences' type: gui {").append(Strings.LN);
			os.append("init {").append(Strings.LN);
			os.append(write);
			os.append("}").append(Strings.LN);
			os.append("}").append(Strings.LN);
			os.flush();
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

}
