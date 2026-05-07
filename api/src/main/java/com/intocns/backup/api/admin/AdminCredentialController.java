package com.intocns.backup.api.admin;

import com.intocns.backup.api.admin.dto.CredentialSummary;
import com.intocns.backup.api.admin.dto.IssuedCredentialResponse;
import com.intocns.backup.application.IssueCredentialUseCase;
import com.intocns.backup.application.ListCredentialsUseCase;
import com.intocns.backup.application.RevokeCredentialUseCase;
import com.intocns.backup.domain.model.HospitalId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/hospitals/{cocode}/credentials")
public class AdminCredentialController {

    private final IssueCredentialUseCase issueCredential;
    private final ListCredentialsUseCase listCredentials;
    private final RevokeCredentialUseCase revokeCredential;

    public AdminCredentialController(IssueCredentialUseCase issueCredential,
                                     ListCredentialsUseCase listCredentials,
                                     RevokeCredentialUseCase revokeCredential) {
        this.issueCredential = issueCredential;
        this.listCredentials = listCredentials;
        this.revokeCredential = revokeCredential;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssuedCredentialResponse issue(@PathVariable long cocode) {
        IssueCredentialUseCase.IssuedCredential issued = issueCredential.issue(new HospitalId(cocode));
        return new IssuedCredentialResponse(issued.clientId(), issued.clientSecret());
    }

    @GetMapping
    public List<CredentialSummary> list(@PathVariable long cocode) {
        return listCredentials.list(new HospitalId(cocode)).stream()
                .map(c -> new CredentialSummary(c.clientId(), c.createdAt()))
                .toList();
    }

    @DeleteMapping("/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable long cocode, @PathVariable String clientId) {
        revokeCredential.revoke(new HospitalId(cocode), clientId);
    }
}
