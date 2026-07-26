package run.halo.links.service;

import run.halo.links.extension.LinkApplication;

final class LinkApplicationUrlOccupancy {

    private LinkApplicationUrlOccupancy() {
    }

    static boolean usesCanonicalUrl(LinkApplication application, String canonicalUrl) {
        if (application == null || application.getSpec() == null) {
            return false;
        }
        var spec = application.getSpec();
        if (LinkUrlCanonicalizer.canonicalKey(spec.getUrl())
            .filter(canonicalUrl::equals)
            .isPresent()) {
            return true;
        }
        var approval = spec.getApproval();
        return approval != null && approval.getRequest() != null
            && LinkUrlCanonicalizer.canonicalKey(approval.getRequest().getUrl())
            .filter(canonicalUrl::equals)
            .isPresent();
    }
}
