-- Journey v2 documents (ADR-0007): the voter ID / driving licence images join the Aadhaar front and back
-- (full images, ADR-0008) and the PAN card in one step, KYC_DOCUMENTS; education now needs the class 10
-- and 12 marksheets and a graduation or diploma certificate.
UPDATE application_step
SET step_key = 'KYC_DOCUMENTS'
WHERE step_key = 'ID_DOCUMENT';

-- Applications still being filled in must add the new files, so their document steps and declaration
-- are open again. Submitted ones keep what they were reviewed with (frozen).
UPDATE application_step s JOIN partner_application a ON a.id = s.application_id
SET s.status = 'IN_PROGRESS', s.completed_at = NULL
WHERE a.status = 'IN_PROGRESS'
  AND s.step_key IN ('KYC_DOCUMENTS', 'EDUCATION', 'DECLARATION')
  AND s.status = 'COMPLETED';
