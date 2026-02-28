# NaiveTomcat's  HRT Tracker

## Overview

This is an Android Application for tracking the intakes of hormone replacement therapy (HRT) medications. It mainly focuses on estrogen levels, and provides a simple interface for users to log their medication intake, set reminders, and simulates the blood estrogen levels using pharmacokinetic modeling.

It can be used by transfeminine individuals undergoing transition and cis-female individuals experiencing menopause, to help them manage their HRT regimen effectively.

## Algorithm and Core Logic

The pharmacokinetic algorithms, mathematical models, and parameters used in this simulation are derived directly from the [HRT-Recorder-PKcomponent-Test](https://github.com/LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test) repository.

We strictly adhere to the PKcore.swift and PKparameter.swift logic provided by **@LaoZhong-Mihari**, ensuring that this kotlin simulation matches the accuracy of the original native implementation (including 3-compartment models, two-part depot kinetics, and specific sublingual absorption tiers).

## Features

- **Medication Logging**: Users can log their HRT medication intake, including the type of medication, dosage, route of ingestion, and time of intake.
- **Medication History**: Users can view their medication history in a clear and organized manner.
- **Medication Planning**: Users can set up a medication plan, specifying the types of medications they intend to take, their dosages, and the schedule for intake.
- **Reminders**: Users can set up reminders for their medication intake, ensuring they never miss a dose.
- **Blood Estrogen Simulation**: The tool simulates the blood estrogen levels based on the logged medication intake and also the medication plan, providing users with insights into how their HRT regimen is affecting their hormone levels.
- **Real-time Visualization**: Users can visualize their simulated blood estrogen levels over time, helping them understand the effects of their medication regimen.
- **Data Export**: Users can export their medication history and simulated hormone levels for personal records or to share with healthcare providers.

## Acknowledgments

This project is inspired by [SmirnovaOyama/Oyama-s-HRT-Tracker](https://github.com/SmirnovaOyama/Oyama-s-HRT-Tracker), built upon this repository and the original PK component test repository [LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test](https://github.com/LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test). We are grateful for the contributions of these developers in creating tools that support the HRT community.

## Future Works

- [ ] Implement cross-platform compatibility to allow users on different devices to access their data seamlessly.
- [ ] Optional online data backup and synchronization features to prevent data loss and allow access from multiple devices.
