import { EditDayView } from "@/components/PlanEditor";

type Props = { params: Promise<{ dayId: string }> };

export const metadata = { title: "Edit day" };

export default async function EditDayPage({ params }: Props) {
  const { dayId } = await params;
  return <EditDayView dayId={dayId} />;
}
