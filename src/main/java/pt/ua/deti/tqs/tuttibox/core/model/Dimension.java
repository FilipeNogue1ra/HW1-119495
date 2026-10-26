package pt.ua.deti.tqs.tuttibox.core.model;

public enum Dimension {
    S,
    M,
    L;

    public boolean canFitIn(Dimension compartmentDimension, boolean allowSToMFallback) {
        if (this == compartmentDimension) {
            return true;
        }
        if (this == S && compartmentDimension == M && allowSToMFallback) {
            return true;
        }
        return false;
    }
}
