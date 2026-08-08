package totah.lab.web.evidence;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import totah.lab.daedalus.evidence.DefaultPocketEvidenceAssembler;

import java.io.IOException;

@Service
public final class PocketEvidenceApplicationService implements PocketEvidenceQuery {
    private final PersistedPocketEvidenceRequestProvider requestProvider;
    private final PocketEvidenceViewMapper viewMapper;
    private final DefaultPocketEvidenceAssembler assembler;

    public PocketEvidenceApplicationService(
            PersistedPocketEvidenceRequestProvider requestProvider) {
        this.requestProvider = requestProvider;
        this.viewMapper = new PocketEvidenceViewMapper();
        this.assembler = new DefaultPocketEvidenceAssembler();
    }

    @Override
    @Transactional(readOnly = true)
    public PocketEvidenceView get(long pocketId) throws IOException {
        return viewMapper.toView(assembler.assemble(requestProvider.load(pocketId)));
    }
}
