package eu.clarin.sru.fcs.aggregator.registry;

/**
 * Endpoint. Contains information about CQL endpoint url and the parent 
 * Institution.
 * 
 * @author Yana Panchenko
 * @author edima
 */
public class Endpoint {

    private String url;

    public Endpoint(String url, Institution institution) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return url;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + (this.url != null ? this.url.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Endpoint other = (Endpoint) obj;
        if ((this.url == null) ? (other.url != null) : !this.url.equals(other.url)) {
            return false;
        }
        return true;
    }
    
}
