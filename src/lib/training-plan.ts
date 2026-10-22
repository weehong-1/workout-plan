import type { Block } from "@/lib/plan";

export type TrainingPlan = {
  id: string;
  name: string;
  description: string;
  blocks: Block[];
  createdAt: number;
};
