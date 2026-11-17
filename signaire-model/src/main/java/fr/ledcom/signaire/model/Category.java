package fr.ledcom.signaire.model;

/** Thematic categories of the SignSuisse lexicon, with the identifiers SignSuisse uses for them. */
public enum Category {
    NATION(6806),
    CITY(6809),
    TRAFFIC(6812),
    PROFESSION_SCHOOL(6815),
    FAMILY(6818),
    SPORT_LEISURE(6821),
    FOOD(6824),
    HOUSEHOLD(6827),
    PEOPLE(6830),
    ANIMALS(6833),
    NATURE(6836),
    HOUSE_BUILDING(6839),
    POLITICS_LAW(6842),
    MEDICINE(6845),
    ECONOMY(6848),
    GENERAL(6851),
    RELIGION(6854),
    VERB(6857),
    PROPERTY(6860),
    EVENTS_MEDIA(6863);

    private final int signSuisseId;

    Category(int signSuisseId) {
        this.signSuisseId = signSuisseId;
    }

    public int signSuisseId() {
        return signSuisseId;
    }

    public static Category fromSignSuisseId(int id) {
        for (Category category : values()) {
            if (category.signSuisseId == id) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown SignSuisse category id: " + id);
    }
}
