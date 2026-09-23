package de.unileipzig.irpsim.core.standingdata.data;

import java.io.IOException;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import de.unileipzig.irpsim.core.simulation.data.TimeInterval;

@Entity
@Table
public class Stammdatum {

	public static class ReferenzSerializer extends JsonSerializer<Stammdatum> {

		@Override
		public void serialize(final Stammdatum stammdatum, final JsonGenerator generator, final SerializerProvider arg2)
				throws IOException, JsonProcessingException {
			generator.writeNumber(stammdatum.getId());
		}
	}

	public static class ParentDeserializer extends JsonDeserializer<Stammdatum> {

		@Override
		public Stammdatum deserialize(final JsonParser parser, final DeserializationContext arg1)
				throws IOException, JsonProcessingException {
			final int id = parser.getIntValue();
			final Stammdatum ret = new Stammdatum();
			ret.setId(id);
			return ret;
		}
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	private String name, typ;

	private TimeInterval zeitintervall;

	private boolean abstrakt;

	@ManyToOne
	@JsonSerialize(using = ReferenzSerializer.class)
	@JsonDeserialize(using = ParentDeserializer.class)
	private Stammdatum referenz = null;

	private Integer bezugsjahr, prognoseHorizont;

	private boolean standardszenario = false;

	/*
	 * Die verantwortlichen Personen wurden durch die Rechteverwaltung ersetzt
	 * (siehe StandingDataOwnershipMigration). Die Felder bleiben nur erhalten,
	 * damit bestehende Zuordnungen einmalig in Zugriffsrechte übernommen werden
	 * können; über die Schnittstelle sind sie weder sichtbar noch änderbar, und
	 * neue Stammdaten erhalten keine Personen mehr.
	 */
	@ManyToOne(cascade = CascadeType.ALL)
	@JsonIgnore
	private Person verantwortlicherPrognosejahr;

	@ManyToOne(cascade = CascadeType.ALL)
	@JsonIgnore
	private Person verantwortlicherBezugsjahr;

	private String setName1;
	private String setName2;

	@Type(type = "text")
	private String kommentar;

	@ElementCollection
	private List<String> setElemente1 = null;

	private boolean setElemente1IsNull = true;

	@ElementCollection
	private List<String> setElemente2 = null;

	private boolean setElemente2IsNull = true;

	/**
	 * Prozent-Angabe, wie viele Zeitreihen des Stammdatums definiert sind. Sollte bei jedem Datenupload aktualisiert werden.
	 */
	private Double vollstaendig = 0d;

	public Stammdatum() {

	}

	/**
	 * Copy-Konstruktor. Erzeugt ein neues Objekt, das z.B. verwendet werden kann um referenzierte Werte zu laden ohne das Original zu überschreiben.
	 * 
	 * @param another Das Stammdatum das kopiert werden soll.
	 */
	public Stammdatum(final Stammdatum another) {
		this.id = another.id;
		this.name = another.name;
		this.typ = another.typ;
		this.zeitintervall = another.zeitintervall;
		this.abstrakt = another.abstrakt;
		this.referenz = another.referenz;
		this.bezugsjahr = another.bezugsjahr;
		this.prognoseHorizont = another.prognoseHorizont;
		this.verantwortlicherPrognosejahr = another.verantwortlicherPrognosejahr;
		this.verantwortlicherBezugsjahr = another.verantwortlicherBezugsjahr;
		this.setName1 = another.setName1;
		this.setName2 = another.setName2;
		this.setElemente1IsNull = another.setElemente1IsNull;
		this.setElemente1 = another.setElemente1;
		this.setElemente2IsNull = another.setElemente2IsNull;
		this.setElemente2 = another.setElemente2;
		this.vollstaendig = another.vollstaendig;
	}

	public Stammdatum(final String name, final String typ, final TimeInterval zeitintervall, final int bezugsjahr,
			final int prognoseHorizont, final String[] szenarien) {
		this.name = name;
		this.typ = typ;
		this.zeitintervall = zeitintervall;
		this.bezugsjahr = bezugsjahr;
		this.prognoseHorizont = prognoseHorizont;
	}

	@JsonIgnore
	public boolean isSetElemente1IsNull() {
		return setElemente1IsNull;
	}

	public void setSetElemente1IsNull(final boolean setElemente1IsNull) {
		this.setElemente1IsNull = setElemente1IsNull;
	}

	@JsonIgnore
	public boolean isSetElemente2IsNull() {
		return setElemente2IsNull;
	}

	public void setSetElemente2IsNull(final boolean setElemente2IsNull) {
		this.setElemente2IsNull = setElemente2IsNull;
	}

	public int getId() {
		return id;
	}

	public void setId(final int id) {
		this.id = id;
	}

	public Integer getPrognoseHorizont() {
		return prognoseHorizont;
	}

	public void setPrognoseHorizont(final Integer prognoseHorizont) {
		this.prognoseHorizont = prognoseHorizont;
	}

	public Stammdatum getReferenz() {
		return referenz;
	}

	public void setReferenz(final Stammdatum referenz) {
		this.referenz = referenz;
	}

	public String getTyp() {
		return typ;
	}

	public void setTyp(final String typ) {
		this.typ = typ;
	}

	public String getName() {
		return name;
	}

	public void setName(final String name) {
		this.name = name;
	}

	public Integer getBezugsjahr() {
		return bezugsjahr;
	}

	public void setBezugsjahr(final Integer bezugsjahr) {
		this.bezugsjahr = bezugsjahr;
	}

	/**
	 * @return Die frühere verantwortliche Person; nur noch für die Übernahme in die Rechteverwaltung
	 */
	@JsonIgnore
	@Deprecated
	public Person getVerantwortlicherPrognosejahr() {
		return verantwortlicherPrognosejahr;
	}

	public boolean isStandardszenario() {
		return standardszenario;
	}

	public void setStandardszenario(final boolean standardszenario) {
		this.standardszenario = standardszenario;
	}

	@JsonIgnore
	@Deprecated
	public void setVerantwortlicherPrognosejahr(final Person verantwortlicherPrognosejahr) {
		this.verantwortlicherPrognosejahr = verantwortlicherPrognosejahr;
	}

	/**
	 * @return Die frühere verantwortliche Person; nur noch für die Übernahme in die Rechteverwaltung
	 */
	@JsonIgnore
	@Deprecated
	public Person getVerantwortlicherBezugsjahr() {
		return verantwortlicherBezugsjahr;
	}

	@JsonIgnore
	@Deprecated
	public void setVerantwortlicherBezugsjahr(final Person verantwortlicherBezugsjahr) {
		this.verantwortlicherBezugsjahr = verantwortlicherBezugsjahr;
	}

	public String getKommentar() {
		return kommentar;
	}

	public void setKommentar(final String kommentar) {
		this.kommentar = kommentar;
	}

	public TimeInterval getZeitintervall() {
		return zeitintervall;
	}

	public void setZeitintervall(final TimeInterval zeitintervall) {
		this.zeitintervall = zeitintervall;
	}

	public boolean isAbstrakt() {
		return abstrakt;
	}

	public void setAbstrakt(final boolean abstrakt) {
		this.abstrakt = abstrakt;
	}

	@Override
	public String toString() {
		return "" + id + "(" + name + ", " + typ + ", " + bezugsjahr + ")";
	}

	/**
	 * Überschreibt alle Werte, bis auf den Typ, mit den Werten des übergebenen Objekts.
	 * 
	 * @param data Objekt mit den Werten, die überschrieben werden sollen.
	 */
	public void copyFrom(final Stammdatum data) {
		setName(data.getName());
		setTyp(data.getTyp());
		setZeitintervall(data.getZeitintervall());
		setAbstrakt(data.isAbstrakt());
		setBezugsjahr(data.getBezugsjahr());
		setPrognoseHorizont(data.getPrognoseHorizont());
		// Die früheren verantwortlichen Personen werden bewusst nicht übernommen:
		// Sie kommen nicht mehr über die Schnittstelle und würden sonst vor ihrer
		// Übernahme in die Rechteverwaltung mit leeren Werten überschrieben.
		setReferenz(data.getReferenz());
		setSetName1(data.getSetName1());
		setSetName2(data.getSetName2());
		kommentar = data.getKommentar();
		standardszenario = data.isStandardszenario();
		setElemente1 = data.getSetElemente1();
		setElemente2 = data.getSetElemente2();
		setElemente1IsNull = data.setElemente1IsNull;
		setElemente2IsNull = data.setElemente2IsNull;
	}

	public Double getVollstaendig() {
		return vollstaendig;
	}

	public void setVollstaendig(final Double vollstaendig) {
		this.vollstaendig = vollstaendig;
	}

	public List<String> getSetElemente1() {
		return setElemente1;
	}

	public void setSetElemente1(final List<String> setElemente1) {
		this.setElemente1 = setElemente1;
	}

	public void setSetElemente2(final List<String> setElemente2) {
		this.setElemente2 = setElemente2;
	}

	public List<String> getSetElemente2() {
		return setElemente2;
	}

	public String getSetName1() {
		return setName1;
	}

	public void setSetName1(final String setName1) {
		this.setName1 = setName1;
	}

	public String getSetName2() {
		return setName2;
	}

	public void setSetName2(final String setName2) {
		this.setName2 = setName2;
	}

	@Override
	public boolean equals(final Object arg0) {
		if (arg0 instanceof Stammdatum) {
			final Stammdatum other = (Stammdatum) arg0;
			final boolean typEquals = other.typ != null ? other.typ.equals(typ) : typ == null;
			final boolean nameEquals = other.name != null ? other.name.equals(name) : name == null;
			final boolean bezugsjahrEquals = other.bezugsjahr != null ? (other.bezugsjahr.intValue() == bezugsjahr.intValue()) : bezugsjahr == null;
			return typEquals && nameEquals && bezugsjahrEquals;
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((bezugsjahr == null) ? 0 : bezugsjahr.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((typ == null) ? 0 : typ.hashCode());
		return result;
	}

	public void setIsNulldata() {
		if (this.getSetElemente1() == null) {
			this.setSetElemente1IsNull(true);
		} else {
			this.setSetElemente1IsNull(false);
		}
		if (this.getSetElemente2() == null) {
			this.setSetElemente2IsNull(true);
		} else {
			this.setSetElemente2IsNull(false);
		}
	}

}
