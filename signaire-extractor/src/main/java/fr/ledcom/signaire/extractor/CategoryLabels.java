package fr.ledcom.signaire.extractor;

import static fr.ledcom.signaire.model.Category.ANIMALS;
import static fr.ledcom.signaire.model.Category.CITY;
import static fr.ledcom.signaire.model.Category.ECONOMY;
import static fr.ledcom.signaire.model.Category.EVENTS_MEDIA;
import static fr.ledcom.signaire.model.Category.FAMILY;
import static fr.ledcom.signaire.model.Category.FOOD;
import static fr.ledcom.signaire.model.Category.GENERAL;
import static fr.ledcom.signaire.model.Category.HOUSEHOLD;
import static fr.ledcom.signaire.model.Category.HOUSE_BUILDING;
import static fr.ledcom.signaire.model.Category.MEDICINE;
import static fr.ledcom.signaire.model.Category.NATION;
import static fr.ledcom.signaire.model.Category.NATURE;
import static fr.ledcom.signaire.model.Category.PEOPLE;
import static fr.ledcom.signaire.model.Category.POLITICS_LAW;
import static fr.ledcom.signaire.model.Category.PROFESSION_SCHOOL;
import static fr.ledcom.signaire.model.Category.PROPERTY;
import static fr.ledcom.signaire.model.Category.RELIGION;
import static fr.ledcom.signaire.model.Category.SPORT_LEISURE;
import static fr.ledcom.signaire.model.Category.TRAFFIC;
import static fr.ledcom.signaire.model.Category.VERB;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import fr.ledcom.signaire.model.Category;

/** Category labels as SignSuisse displays them in French, German and Italian. */
final class CategoryLabels {

    private static final Map<String, Category> BY_LABEL = labels();

    private CategoryLabels() {
    }

    static Optional<Category> fromLabel(String label) {
        return Optional.ofNullable(BY_LABEL.get(normalize(label)));
    }

    private static Map<String, Category> labels() {
        Map<String, Category> labels = new HashMap<>();
        add(labels, NATION, "Nation", "Nation", "Nazione");
        add(labels, CITY, "Ville", "Stadt", "Città");
        add(labels, TRAFFIC, "Circulation", "Verkehr", "Circolazione");
        add(labels, PROFESSION_SCHOOL, "Profession/École", "Beruf/Schule", "Professione/Scuola");
        add(labels, FAMILY, "Famille", "Familie", "Famiglia");
        add(labels, SPORT_LEISURE, "Sport/Temps libre", "Sport/Freizeit", "Sport/Tempo libero");
        add(labels, FOOD, "Nourriture", "Nahrung", "Cibo");
        add(labels, HOUSEHOLD, "Ménage", "Haushalt", "Economia domestica");
        add(labels, PEOPLE, "Gens", "Menschen", "Persone");
        add(labels, ANIMALS, "Animaux", "Tiere", "Animali");
        add(labels, NATURE, "Nature", "Natur", "Natura");
        add(labels, HOUSE_BUILDING, "Maison/Bâtiment", "Haus/Gebäude", "Casa/Edificio");
        add(labels, POLITICS_LAW, "Politique/Droit", "Politik/Recht", "Politica/Diritto");
        add(labels, MEDICINE, "Médecine", "Medizin", "Medicina");
        add(labels, ECONOMY, "Économie", "Wirtschaft", "Economia");
        add(labels, GENERAL, "En général", "Allgemein", "In generale");
        add(labels, RELIGION, "Religion", "Religion", "Religione");
        add(labels, VERB, "Verbe", "Verb", "Verbo");
        add(labels, PROPERTY, "Propriété", "Eigenschaft", "Proprietà");
        add(labels, EVENTS_MEDIA, "Événements/Média", "Veranstaltung/Medien", "Eventi/Media");
        return Map.copyOf(labels);
    }

    private static void add(Map<String, Category> labels, Category category, String... translations) {
        for (String label : translations) {
            labels.put(normalize(label), category);
        }
    }

    private static String normalize(String label) {
        return label.strip().toLowerCase(Locale.ROOT);
    }
}
