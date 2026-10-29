import { Box, Link, Select, Table, TableContainer, Tbody, Td, Text, Th, Thead, Tr } from "@chakra-ui/react";
import NextLink from "next/link";
import { useEffect, useState } from "react";
import { StatusPill } from "@/components/StatusPill";
import { StaffShell } from "@/features/staff/StaffShell";
import type { QueueItem } from "@/features/staff/types";
import { call } from "@/lib/api";

// "BLOCKED": applications still with the applicant that have a step blocked (staff follow up)
const FILTERS = ["", "SUBMITTED", "UNDER_REVIEW", "ON_HOLD", "CORRECTION_REQUIRED", "BLOCKED", "APPROVED", "REJECTED"];

/** Applications waiting for review, oldest first. */
export default function QueuePage() {
	const [status, setStatus] = useState("");
	const [items, setItems] = useState<QueueItem[] | null>(null);
	useEffect(() => {
		call<QueueItem[]>(`/v1/admin/applications${status ? `?status=${status}` : ""}`)
			.then((reply) => setItems(reply.data))
			.catch(() => setItems([]));
	}, [status]);
	return (
		<StaffShell title="Applications">
			{() => (
				<>
					<Box maxW="280px" mb={4}>
						<Select aria-label="Show" value={status} onChange={(event) => setStatus(event.target.value)} size="md">
							{FILTERS.map((value) => (
								<option key={value} value={value}>
									{value ? value.toLowerCase().replace(/_/g, " ") : "Waiting for review"}
								</option>
							))}
						</Select>
					</Box>
					{items && items.length === 0 && <Text color="text.muted">Nothing here right now.</Text>}
					{items && items.length > 0 && (
						<TableContainer bg="bg.surface" border="1px solid" borderColor="border.hairline" borderRadius="card">
							<Table size="sm">
								<Thead bg="bg.subtle">
									<Tr>
										<Th>Application</Th>
										<Th>Status</Th>
										<Th>Path</Th>
										<Th>Submitted</Th>
										<Th>Reviewer</Th>
										<Th isNumeric>Open items</Th>
									</Tr>
								</Thead>
								<Tbody>
									{items.map((item) => (
										<Tr key={item.application_id}>
											<Td>
												<Link as={NextLink} href={`/staff/cases/${item.application_id}`} color="text.link" fontFamily="mono">
													{item.application_number ?? item.application_id}
												</Link>
											</Td>
											<Td>
												<StatusPill status={item.status} />
											</Td>
											<Td>{item.method === "DIGILOCKER" ? "DigiLocker" : "Step by step"}</Td>
											<Td>{item.submitted_at ? new Date(item.submitted_at).toLocaleString("en-IN") : "-"}</Td>
											<Td>{item.assigned_to ?? "-"}</Td>
											<Td isNumeric>{item.waiting_items}</Td>
										</Tr>
									))}
								</Tbody>
							</Table>
						</TableContainer>
					)}
				</>
			)}
		</StaffShell>
	);
}
